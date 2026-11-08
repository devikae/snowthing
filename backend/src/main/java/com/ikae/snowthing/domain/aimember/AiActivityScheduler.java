package com.ikae.snowthing.domain.aimember;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.aimember.AiActivityPlanner.Kind;
import com.ikae.snowthing.domain.aimember.AiActivityPlanner.Planned;
import com.ikae.snowthing.domain.aimember.AiMemberRoster.AiMember;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.CommentView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.PostView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.Reaction;
import com.ikae.snowthing.domain.aimember.persona.Persona.Activity;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.service.ChatService;

import lombok.extern.slf4j.Slf4j;

/**
 * 1분마다 할 일을 고른다. 한 번에 계획된 행동 하나와 댓글 하나, 채팅 반응 하나까지만 실행해 몰리지 않게 한다. 실행은 AI 멤버 전용 스레드에서 하므로 공용 스케줄러
 * 스레드를 막지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = AiMemberConfig.ENABLED_PROPERTY, havingValue = "true")
public class AiActivityScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final double CHAT_REACTION_CHANCE = 0.3;
    private static final String BOT_SENDER_ID = "snowthing-ai";

    private final AiMemberRoster roster;
    private final AiMemberQueryRepository queryRepository;
    private final AiMemberActionService actionService;
    private final ChatService chatService;
    private final ScheduledExecutorService aiMemberExecutor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    // 아래 상태는 AI 멤버 스레드에서만 읽고 쓴다.
    private LocalDate plannedDate;
    private final List<Planned> pending = new ArrayList<>();
    private final Set<String> failedReactions = new HashSet<>();
    private String lastChatReaction;

    public AiActivityScheduler(
            AiMemberRoster roster,
            AiMemberQueryRepository queryRepository,
            AiMemberActionService actionService,
            ChatService chatService,
            ScheduledExecutorService aiMemberExecutor,
            Clock clock) {
        this.roster = roster;
        this.queryRepository = queryRepository;
        this.actionService = actionService;
        this.chatService = chatService;
        this.aiMemberExecutor = aiMemberExecutor;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void tick() {
        if (roster.members().isEmpty() || !running.compareAndSet(false, true)) {
            return;
        }
        aiMemberExecutor.execute(
                () -> {
                    try {
                        runDue(LocalDateTime.now(clock.withZone(KST)));
                    } catch (Exception exception) {
                        log.warn(
                                "AI member tick failed: type={}",
                                exception.getClass().getSimpleName());
                    } finally {
                        running.set(false);
                    }
                });
    }

    void runDue(LocalDateTime now) {
        List<AiMember> members = roster.members();
        List<AiMember> active =
                members.stream()
                        .filter(m -> m.persona().activity().activeAt(now.getHour()))
                        .toList();
        if (active.isEmpty()) {
            return;
        }
        List<Long> memberIds = members.stream().map(AiMember::memberId).sorted().toList();
        if (!now.toLocalDate().equals(plannedDate)) {
            replan(now, members, memberIds);
        }
        runPlanned(now, active);
        runReaction(now, memberIds, active);
        runChatReaction(now, active);
    }

    private void replan(LocalDateTime now, List<AiMember> members, List<Long> memberIds) {
        LocalDate today = now.toLocalDate();
        Map<Long, Integer> posted =
                queryRepository.countPostsSince(memberIds, today.atStartOfDay());
        pending.clear();
        Map<Long, Activity> activities = new LinkedHashMap<>();
        members.stream()
                .sorted(Comparator.comparingLong(AiMember::memberId))
                .forEach(m -> activities.put(m.memberId(), m.persona().activity()));
        pending.addAll(AiActivityPlanner.plan(today, activities, posted, now));
        failedReactions.clear();
        plannedDate = today;
        log.info("AI member plan: date={}, actions={}", today, pending.size());
    }

    private void runPlanned(LocalDateTime now, List<AiMember> active) {
        if (pending.isEmpty() || pending.get(0).at().isAfter(now)) {
            return;
        }
        Planned next = pending.remove(0);
        if (next.kind() == Kind.POST) {
            roster.byMemberId(next.memberId())
                    .ifPresent(member -> actionService.writePost(member, now));
            return;
        }
        List<AiMember> shuffled = new ArrayList<>(active);
        Collections.shuffle(shuffled);
        int participants = Math.min(shuffled.size(), 2 + new SplittableRandom().nextInt(2));
        actionService.chat(
                shuffled.subList(0, participants), 3 + new SplittableRandom().nextInt(4), now);
    }

    private void runReaction(LocalDateTime now, List<Long> memberIds, List<AiMember> active) {
        List<PostView> posts = queryRepository.findRecentPosts(now.minusDays(1));
        if (posts.isEmpty()) {
            return;
        }
        Map<Long, List<CommentView>> comments =
                queryRepository.findComments(posts.stream().map(PostView::postId).toList()).stream()
                        .collect(Collectors.groupingBy(CommentView::postId));
        Map<Long, Integer> today =
                queryRepository.countCommentsSince(memberIds, now.toLocalDate().atStartOfDay());
        Set<Long> activeIds = active.stream().map(AiMember::memberId).collect(Collectors.toSet());
        Reaction reaction =
                AiReactionPolicy.next(
                                posts,
                                comments,
                                memberIds,
                                today,
                                failedReactions,
                                now,
                                activeIds::contains)
                        .orElse(null);
        if (reaction == null) {
            return;
        }
        boolean written = false;
        try {
            written =
                    actionService.writeComment(
                            reaction,
                            comments.getOrDefault(reaction.post().postId(), List.of()),
                            now);
        } finally {
            if (!written) {
                failedReactions.add(reaction.key());
            }
        }
    }

    /** 사람이 마지막으로 말했으면 1~5분 뒤 30% 확률로 한 마디 받아준다. 같은 메시지에는 한 번만 시도한다. */
    private void runChatReaction(LocalDateTime now, List<AiMember> members) {
        List<ChatMessageResponse> recent = chatService.getRecentMessages();
        if (recent.isEmpty()) {
            return;
        }
        ChatMessageResponse last = recent.get(recent.size() - 1);
        String senderId = last.sender() != null ? last.sender().publicId() : null;
        if (senderId == null
                || BOT_SENDER_ID.equals(senderId)
                || roster.publicIds().contains(senderId)
                || last.messageId().equals(lastChatReaction)) {
            return;
        }
        // 정치 얘기를 걸면 받아주지 않는다. 그 메시지에는 다시 시도하지 않는다.
        if (PoliticalContentFilter.containsPolitics(last.content())) {
            lastChatReaction = last.messageId();
            return;
        }
        LocalDateTime sentAt = LocalDateTime.parse(last.sentAt());
        if (sentAt.isBefore(now.minusMinutes(10))) {
            return;
        }
        SplittableRandom random = new SplittableRandom(last.messageId().hashCode());
        if (random.nextDouble() >= CHAT_REACTION_CHANCE) {
            lastChatReaction = last.messageId();
            return;
        }
        if (now.isBefore(sentAt.plusMinutes(1 + random.nextInt(5)))) {
            return;
        }
        lastChatReaction = last.messageId();
        actionService.chat(List.of(members.get(random.nextInt(members.size()))), 1, now);
    }
}
