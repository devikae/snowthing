package com.ikae.snowthing.domain.columnrepost;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostResponse;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.notification.DiscordNotifier;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.extern.slf4j.Slf4j;

/**
 * 대기열에서 다음 칼럼 하나를 휘구리 계정 글로 올린다(IKA-112).
 *
 * <p>순서: 대기 글을 고른다 → 디시 사진을 받아 눈팅 사진으로 올린다(트랜잭션 밖, 네트워크) → 한 트랜잭션에서 줄을 잠그고 아직 대기인지 다시 본 뒤 글을 쓰고
 * 게시됨으로 바꾼다. 두 번 불려도 두 번째는 잠금 뒤 상태 확인에서 빠진다.
 *
 * <p>실패하면 그 글을 대기로 두고 다음 게시 시각에 다시 시도한다. 순서를 지키려고 건너뛰지 않고, 정해진 횟수가 차면 그 글에서 멈춘다.
 */
@Slf4j
@Service
public class ColumnRepostService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String SOURCE_URL =
            "https://gall.dcinside.com/mgallery/board/view/?id=snowboard&no=";

    private final ColumnRepostRepository repository;
    private final ColumnRepostImages images;
    private final PostService postService;
    private final MemberRepository memberRepository;
    private final ColumnRepostProperties properties;
    private final DiscordNotifier notifier;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ColumnRepostService(
            ColumnRepostRepository repository,
            ColumnRepostImages images,
            PostService postService,
            MemberRepository memberRepository,
            ColumnRepostProperties properties,
            DiscordNotifier notifier,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.repository = repository;
        this.images = images;
        this.postService = postService;
        this.memberRepository = memberRepository;
        this.properties = properties;
        this.notifier = notifier;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** 게시 결과. 테스트와 로그용. */
    public enum Result {
        PUBLISHED,
        EMPTY,
        STOPPED,
        SKIPPED,
        FAILED
    }

    public long pendingCount() {
        return repository.countByStatus(ColumnRepostItem.PENDING);
    }

    public Result publishNext() {
        Optional<ColumnRepostItem> next =
                repository.findFirstByStatusOrderBySequenceNoAscIdAsc(ColumnRepostItem.PENDING);
        if (next.isEmpty()) {
            return Result.EMPTY;
        }
        ColumnRepostItem item = next.get();
        if (item.getAttempts() >= properties.maxAttempts()) {
            log.warn(
                    "Column repost stopped: sequence={}, attempts={}",
                    item.getSequenceNo(),
                    item.getAttempts());
            return Result.STOPPED;
        }
        try {
            Member member =
                    memberRepository
                            .findByEmail(properties.memberEmail())
                            .orElseThrow(() -> new IllegalStateException("휘구리 계정을 찾지 못함"));
            String sourceUrl = SOURCE_URL + item.getSourceNo();
            String content = images.replace(item.getContent(), sourceUrl, member.getEmail());
            Result result = tx.execute(status -> publish(item.getId(), content, member));
            return result == null ? Result.SKIPPED : result;
        } catch (Exception failure) {
            recordFailure(item, failure);
            return Result.FAILED;
        }
    }

    private Result publish(Long id, String content, Member member) {
        ColumnRepostItem locked = repository.findByIdForUpdate(id).orElse(null);
        if (locked == null || !locked.isPending()) {
            return Result.SKIPPED;
        }
        PostResponse post =
                postService.createPost(
                        new PostCreateRequest(
                                properties.categoryCode(),
                                locked.getTitle(),
                                content,
                                false,
                                null,
                                List.of()),
                        new CustomUserDetails(member),
                        null);
        locked.markPublished(post.publicId(), LocalDateTime.now(clock.withZone(KST)));
        long total = repository.count();
        events.publishEvent(
                new ColumnRepostedEvent(
                        locked.getSequenceNo(),
                        total,
                        locked.getTitle(),
                        properties.linkBaseUrl() + "/posts/" + post.publicId()));
        log.info(
                "Column reposted: sequence={}, source={}, post={}",
                locked.getSequenceNo(),
                locked.getSourceNo(),
                post.publicId());
        return Result.PUBLISHED;
    }

    private void recordFailure(ColumnRepostItem item, Exception failure) {
        String error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        log.warn(
                "Column repost failed: sequence={}, error={}",
                item.getSequenceNo(),
                failure.getClass().getSimpleName());
        Integer attempts =
                tx.execute(
                        status -> {
                            ColumnRepostItem locked =
                                    repository.findByIdForUpdate(item.getId()).orElse(null);
                            if (locked == null || !locked.isPending()) return null;
                            locked.recordFailure(error);
                            return locked.getAttempts();
                        });
        if (attempts == null) return;
        boolean stopped = attempts >= properties.maxAttempts();
        notifier.alert(
                "⚠️ 휘구리 칼럼 게시 실패 "
                        + attempts
                        + "/"
                        + properties.maxAttempts()
                        + (stopped ? " (멈춤, 확인 필요)" : " (다음 게시 시각에 다시 시도)")
                        + "\n순번: "
                        + item.getSequenceNo()
                        + "\n제목: "
                        + item.getTitle()
                        + "\n오류: "
                        + failure.getClass().getSimpleName());
    }
}
