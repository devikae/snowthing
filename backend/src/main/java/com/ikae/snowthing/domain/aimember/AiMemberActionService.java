package com.ikae.snowthing.domain.aimember;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.aimember.AiMemberRoster.AiMember;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.CommentView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.Reaction;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.ComposeRequest;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.ComposeResponse;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.Context;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.Line;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.Message;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.PersonaPayload;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.PostThread;
import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.config.AiMemberProperties;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.SharedRequestLimiter;
import com.ikae.snowthing.global.util.ContactFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 멤버 행동 하나를 실행한다: 호출 상한 → compose → 연락처·중복 검사 → 기존 서비스로 저장. 실패하면 로그만 남기고 건너뛴다(재시도 없음).
 *
 * <p>사람이 쓰는 경로(PostService, CommentService, ChatService)를 그대로 지나가므로 검증, 검색 색인, 댓글 수가 사람 글과 똑같이
 * 처리된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = AiMemberConfig.ENABLED_PROPERTY, havingValue = "true")
public class AiMemberActionService {

    static final String CHAT_DESTINATION = "/sub/chat/main";
    static final String BUDGET_KEY = "snowthing:limits:{ai-members}:global:day";
    private static final String ANONYMOUS_BOARD = "ANONYMOUS";

    private final AiMemberProperties properties;
    private final AiComposeGateway gateway;
    private final AiMemberQueryRepository queryRepository;
    private final AiMemberRoster roster;
    private final MemberRepository memberRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SharedRequestLimiter sharedLimiter;
    private final ScheduledExecutorService aiMemberExecutor;

    public void writePost(AiMember member, LocalDateTime now) {
        if (!isActive(member)) {
            return;
        }
        String board =
                AiMemberQueryRepository.BOARDS.get(
                        ThreadLocalRandom.current().nextInt(AiMemberQueryRepository.BOARDS.size()));
        Context context = context(now);
        ComposeResponse response =
                compose(
                        new ComposeRequest(
                                requestId(),
                                "POST",
                                List.of(payload(member)),
                                board,
                                null,
                                null,
                                null,
                                context));
        if (response == null || response.title() == null || response.content() == null) {
            return;
        }
        if (ContactFilter.containsAnyContact(response.title() + "\n" + response.content())
                || context.recentTitles().contains(response.title().strip())) {
            log.info("AI member post dropped: key={}", member.persona().key());
            return;
        }
        postService.createPost(
                new PostCreateRequest(
                        board, response.title(), response.content(), false, null, List.of()),
                userDetails(member),
                member.persona().writerIp());
        log.info("AI member posted: key={}, board={}", member.persona().key(), board);
    }

    /**
     * @return 댓글을 저장했으면 true
     */
    public boolean writeComment(Reaction reaction, List<CommentView> comments, LocalDateTime now) {
        AiMember member = roster.byMemberId(reaction.memberId()).orElse(null);
        if (member == null || !isActive(member)) {
            return false;
        }
        Labels labels = new Labels(member);
        List<Message> thread = new ArrayList<>();
        for (CommentView comment : comments) {
            thread.add(new Message(labels.of(comment.memberId()), comment.content()));
        }
        ComposeResponse response =
                compose(
                        new ComposeRequest(
                                requestId(),
                                "COMMENT",
                                List.of(payload(member)),
                                reaction.post().board(),
                                new PostThread(
                                        reaction.post().title(),
                                        "[작성자: "
                                                + labels.of(reaction.post().memberId())
                                                + "]\n"
                                                + truncate(reaction.post().content(), 3_900),
                                        thread.subList(
                                                Math.max(0, thread.size() - 30), thread.size())),
                                null,
                                null,
                                context(now)));
        if (response == null || response.content() == null) {
            return false;
        }
        if (ContactFilter.containsAnyContact(response.content())) {
            log.info("AI member comment dropped: key={}", member.persona().key());
            return false;
        }
        commentService.createComment(
                reaction.post().publicId(),
                new CommentCreateRequest(
                        reaction.parentCommentId(),
                        response.content(),
                        ANONYMOUS_BOARD.equals(reaction.post().board()),
                        null),
                userDetails(member),
                member.persona().writerIp());
        log.info(
                "AI member commented: key={}, postId={}, reply={}",
                member.persona().key(),
                reaction.post().postId(),
                reaction.parentCommentId() != null);
        return true;
    }

    /** 대화를 한 번에 만들고 20~90초 간격으로 나눠 보낸다. */
    public void chat(List<AiMember> candidates, int lineCount, LocalDateTime now) {
        List<AiMember> participants = candidates.stream().filter(this::isActive).toList();
        if (participants.isEmpty()) {
            return;
        }
        Map<String, AiMember> byKey = new HashMap<>();
        participants.forEach(member -> byKey.put(member.persona().key(), member));
        Map<String, String> publicIdToKey = new HashMap<>();
        roster.members()
                .forEach(member -> publicIdToKey.put(member.publicId(), member.persona().key()));
        Map<String, String> others = new HashMap<>();
        List<Message> recent = new ArrayList<>();
        for (ChatMessageResponse message : chatService.getRecentMessages()) {
            String publicId = message.sender() != null ? message.sender().publicId() : "";
            String speaker =
                    publicIdToKey.containsKey(publicId)
                            ? publicIdToKey.get(publicId)
                            : others.computeIfAbsent(publicId, id -> "회원" + (others.size() + 1));
            recent.add(new Message(speaker, message.content()));
        }
        ComposeResponse response =
                compose(
                        new ComposeRequest(
                                requestId(),
                                "CHAT",
                                participants.stream().map(AiMemberActionService::payload).toList(),
                                null,
                                null,
                                recent,
                                lineCount,
                                context(now)));
        if (response == null || response.lines() == null) {
            return;
        }
        long delaySeconds = 0;
        for (Line line : response.lines()) {
            AiMember speaker = byKey.get(line.speaker());
            if (speaker == null
                    || line.text() == null
                    || ContactFilter.containsAnyContact(line.text())) {
                continue;
            }
            aiMemberExecutor.schedule(
                    () -> sendChat(speaker, line.text()), delaySeconds, TimeUnit.SECONDS);
            delaySeconds += ThreadLocalRandom.current().nextLong(20, 91);
        }
    }

    private void sendChat(AiMember member, String text) {
        try {
            if (!isActive(member)) {
                return;
            }
            ChatMessageResponse message =
                    chatService.processMessage(
                            new ChatMessageRequest(null, text), userDetails(member), null);
            messagingTemplate.convertAndSend(CHAT_DESTINATION, message);
        } catch (Exception exception) {
            log.warn(
                    "AI member chat failed: key={}, type={}",
                    member.persona().key(),
                    exception.getClass().getSimpleName());
        }
    }

    private ComposeResponse compose(ComposeRequest request) {
        var decision =
                sharedLimiter.acquire(
                        List.of(
                                new SharedRequestLimiter.Limit(
                                        BUDGET_KEY,
                                        properties.dailyCallLimit(),
                                        Duration.ofDays(1))));
        if (!decision.allowed()) {
            log.info(
                    "AI member budget exhausted: retryAfterSeconds={}",
                    decision.retryAfterSeconds());
            return null;
        }
        try {
            return gateway.compose(request);
        } catch (Exception exception) {
            log.warn(
                    "AI member compose failed: action={}, type={}",
                    request.action(),
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private Context context(LocalDateTime now) {
        return new Context(
                now.toLocalDate().toString(),
                season(now.getMonth()),
                queryRepository.findResortNames(),
                queryRepository.findRecentSnowReports(now.minusDays(1)),
                queryRepository.findTitlesSince(roster.memberIds(), now.minusDays(7)),
                properties.topicKeywords());
    }

    static String season(Month month) {
        return switch (month) {
            case NOVEMBER -> "시즌 개장 무렵";
            case DECEMBER, JANUARY -> "시즌 한창";
            case FEBRUARY -> "시즌 후반";
            case MARCH -> "폐장 무렵";
            default -> "비시즌";
        };
    }

    /** 명단은 기동 때 만들어지므로 그 뒤의 정지는 여기서 본다. 유료 compose 호출 전에 확인한다. */
    private boolean isActive(AiMember member) {
        return memberRepository.findStatusById(member.memberId()).orElse(null)
                == MemberStatus.ACTIVE;
    }

    private CustomUserDetails userDetails(AiMember member) {
        return new CustomUserDetails(memberRepository.findById(member.memberId()).orElseThrow());
    }

    private static PersonaPayload payload(AiMember member) {
        return new PersonaPayload(member.persona().key(), member.persona().description());
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** 자기 글은 페르소나 키, 다른 AI는 그 키, 사람은 "회원N"·"비회원"으로 부른다. 실제 닉네임은 보내지 않는다. */
    private final class Labels {
        private final AiMember self;
        private final Map<Long, String> people = new HashMap<>();

        Labels(AiMember self) {
            this.self = self;
        }

        String of(Long memberId) {
            if (memberId == null) {
                return "비회원";
            }
            if (memberId == self.memberId()) {
                return self.persona().key();
            }
            return roster.byMemberId(memberId)
                    .map(member -> member.persona().key())
                    .orElseGet(
                            () ->
                                    people.computeIfAbsent(
                                            memberId, id -> "회원" + (people.size() + 1)));
        }
    }
}
