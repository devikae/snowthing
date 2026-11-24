package com.ikae.snowthing.domain.contentfilter.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.contentfilter.entity.ContentFilterHold;
import com.ikae.snowthing.domain.contentfilter.entity.FilterTargetType;
import com.ikae.snowthing.domain.contentfilter.repository.ContentFilterHoldRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.service.InquiryAccess;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.util.ContactFilter;

import lombok.RequiredArgsConstructor;

/**
 * 금칙어 조용한 보류 (IKA-96).
 *
 * <ul>
 *   <li>저장은 평소처럼 성공하고 응답도 같다. 다른 사람에게만 보이지 않는다. 광고를 올린 사람이 걸린 것을 알아채고 표현을 바꾸지 못하게 하려는 선택이다.
 *   <li>관리자는 검사하지 않는다. AI 회원은 다른 회원과 똑같이 검사한다.
 *   <li>작성자 본인은 보류된 글을 평소처럼 본다. 회원은 회원 ID로 본다(로그인 세션만, 봇 위임과 API 토큰은 제외). 비회원은 같은 IP에서 보류 뒤 {@link
 *       #ANONYMOUS_AUTHOR_WINDOW} 안에만 본다. 비회원은 계정이 없어 IP 말고 본인을 알 방법이 없고, 같은 IP를 쓰는 다른 사람에게 오래 보이지
 *       않게 시간을 짧게 둔다.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ContentFilterService {

    public static final Duration ANONYMOUS_AUTHOR_WINDOW = Duration.ofHours(1);

    /** 연락처로 보류한 항목의 걸린 말 앞에 붙인다. 관리자 보류 목록에서 금칙어와 구분한다. */
    public static final String CONTACT_REASON_PREFIX = "연락처: ";

    private final BannedWordService bannedWords;
    private final ContentFilterHoldRepository holds;
    private final Clock clock;

    /**
     * 가입한 지 이 시간이 안 된 회원은 비회원처럼 연락처를 검사한다 (IKA-122). 기본 24시간은 자동 검열의 새 계정 기준과 같다. 0이면 회원은 검사하지 않는다.
     */
    @Value("${snowthing.content-filter.contact-new-member-age:24h}")
    private Duration contactNewMemberAge;

    /** 걸린 금칙어. 관리자이거나 걸린 말이 없으면 빈 값. */
    public Optional<String> inspect(CustomUserDetails writer, String... texts) {
        if (InquiryAccess.isAdmin(writer)) {
            return Optional.empty();
        }
        return bannedWords.findMatch(texts);
    }

    /**
     * 글과 댓글 검사 (IKA-122). 금칙어에 더해, 비회원이나 가입한 지 얼마 안 된 회원이면 연락처도 본다. 광고는 업체 이름이 바뀌어도 메신저 아이디나 전화번호를
     * 꼭 남기기 때문이다. 오래된 회원은 카풀, 장터, 모임 글에 연락처를 쓰는 게 정상이라 보지 않는다.
     *
     * @param author 글이나 댓글의 회원. 비회원이면 null
     * @return 걸린 금칙어, 또는 "연락처: 메신저 ID" 같은 걸린 이유. 관리자이거나 걸린 게 없으면 빈 값
     */
    public Optional<String> inspectPosting(
            CustomUserDetails writer, Member author, String... texts) {
        if (InquiryAccess.isAdmin(writer)) {
            return Optional.empty();
        }
        Optional<String> word = bannedWords.findMatch(texts);
        if (word.isPresent() || !isUnprovenAuthor(author)) {
            return word;
        }
        for (String text : texts) {
            Optional<String> contact = ContactFilter.findPostingContact(text);
            if (contact.isPresent()) {
                return Optional.of(CONTACT_REASON_PREFIX + contact.get());
            }
        }
        return Optional.empty();
    }

    private boolean isUnprovenAuthor(Member author) {
        return author == null
                || author.getCreatedAt() == null
                || author.getCreatedAt().isAfter(now().minus(contactNewMemberAge));
    }

    /** 보류 기록을 남기거나, 이미 있으면 걸린 말과 시각을 새로 쓴다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPostHold(Post post, String matchedWord) {
        record(
                FilterTargetType.POST,
                post.getId(),
                post.getId(),
                post.getMember() == null ? null : post.getMember().getId(),
                post.getWriterIp(),
                matchedWord);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCommentHold(Comment comment, String matchedWord) {
        record(
                FilterTargetType.COMMENT,
                comment.getId(),
                comment.getPost().getId(),
                comment.getMember() == null ? null : comment.getMember().getId(),
                comment.getWriterIp(),
                matchedWord);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void clearHold(FilterTargetType targetType, Long targetId) {
        holds.deleteByTarget(targetType, targetId);
    }

    /** 보류 글을 이 사람이 볼 수 있는지. 관리자와 작성자 본인만 true. */
    public boolean canSeeHeldPost(Post post, CustomUserDetails viewer, String viewerIp) {
        if (post.getStatus() != PostStatus.FILTER_HOLD) {
            return false;
        }
        if (InquiryAccess.isAdmin(viewer)) {
            return true;
        }
        if (post.getMember() != null) {
            return InquiryAccess.isSessionUser(viewer)
                    && Objects.equals(post.getMember().getId(), viewer.getMemberId());
        }
        return holds.findByTargetTypeAndTargetId(FilterTargetType.POST, post.getId())
                .filter(hold -> hold.getMemberId() == null)
                .filter(hold -> viewerIp != null && viewerIp.equals(hold.getWriterIp()))
                .filter(hold -> !hold.getHeldAt().isBefore(anonymousSince()))
                .isPresent();
    }

    /** 게시판 첫 쪽에 끼워 넣을 본인 보류 글. 본인을 알 수 없으면 빈 목록. */
    @Transactional(readOnly = true)
    public List<Post> heldPostsOfViewer(CustomUserDetails viewer, String viewerIp) {
        Long memberId = InquiryAccess.isSessionUser(viewer) ? viewer.getMemberId() : null;
        if (memberId == null && (viewerIp == null || viewerIp.isBlank())) {
            return List.of();
        }
        return holds.findHeldPostsOfViewer(memberId, viewerIp, anonymousSince());
    }

    /** 댓글 목록 쿼리에 넘길 보는 사람 정보. */
    public CommentViewer commentViewer(CustomUserDetails viewer, String viewerIp) {
        Long memberId = InquiryAccess.isSessionUser(viewer) ? viewer.getMemberId() : null;
        return new CommentViewer(memberId, viewerIp, anonymousSince());
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDateTime anonymousSince() {
        return now().minus(ANONYMOUS_AUTHOR_WINDOW);
    }

    private void record(
            FilterTargetType type,
            Long targetId,
            Long postId,
            Long memberId,
            String writerIp,
            String matchedWord) {
        LocalDateTime now = now();
        Optional<ContentFilterHold> existing = holds.findByTargetTypeAndTargetId(type, targetId);
        if (existing.isPresent()) {
            existing.get().refresh(matchedWord, now);
            return;
        }
        holds.save(
                new ContentFilterHold(
                        type, targetId, postId, memberId, writerIp, matchedWord, now));
    }

    /**
     * 댓글 목록에서 보류 댓글을 보여 줄 사람. 회원 ID가 같거나, 비회원 댓글이고 IP가 같으며 보류 뒤 짧은 시간 안일 때만 보인다.
     *
     * @param anonymousSince 비회원 작성자 확인 시작 시각 (comment.updated_at과 비교)
     */
    public record CommentViewer(Long memberId, String ip, LocalDateTime anonymousSince) {}
}
