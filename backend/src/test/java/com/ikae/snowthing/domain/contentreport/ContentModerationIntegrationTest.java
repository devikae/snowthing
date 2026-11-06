package com.ikae.snowthing.domain.contentreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.dto.PostCommentListResponse;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.contentreport.dto.AdminContentReportResponse;
import com.ikae.snowthing.domain.contentreport.dto.AdminContentReportResponse.TargetState;
import com.ikae.snowthing.domain.contentreport.dto.ContentReportCreateRequest;
import com.ikae.snowthing.domain.contentreport.dto.ModerationActionRequest;
import com.ikae.snowthing.domain.contentreport.entity.ContentModerationAction;
import com.ikae.snowthing.domain.contentreport.entity.ModerationActionType;
import com.ikae.snowthing.domain.contentreport.entity.ReportReason;
import com.ikae.snowthing.domain.contentreport.entity.ReportStatus;
import com.ikae.snowthing.domain.contentreport.entity.ReportTargetType;
import com.ikae.snowthing.domain.contentreport.repository.ContentModerationActionRepository;
import com.ikae.snowthing.domain.contentreport.service.ContentModerationService;
import com.ikae.snowthing.domain.contentreport.service.ContentReportService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.notification.entity.Notification;
import com.ikae.snowthing.domain.notification.entity.NotificationType;
import com.ikae.snowthing.domain.notification.repository.NotificationRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 신고부터 임시조치, 공시, 통지, 복구까지 실제 DB로 확인한다. */
@SpringBootTest
@Transactional
class ContentModerationIntegrationTest {

    @Autowired private ContentReportService reportService;
    @Autowired private ContentModerationService moderationService;
    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private PostRepository postRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ContentModerationActionRepository actionRepository;
    @Autowired private EntityManager entityManager;

    private Member author;
    private Member reporter;
    private Member admin;
    private CustomUserDetails authorUser;
    private CustomUserDetails reporterUser;
    private String postPublicId;

    @BeforeEach
    void setUp() {
        author = saveMember("author", Role.ROLE_USER);
        reporter = saveMember("reporter", Role.ROLE_USER);
        admin = saveMember("admin", Role.ROLE_ADMIN);
        authorUser = new CustomUserDetails(author);
        reporterUser = new CustomUserDetails(reporter);
        categoryRepository
                .findByCode("FREE")
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder().name("자유게시판").code("FREE").build()));
        postPublicId =
                postService
                        .createPost(
                                new PostCreateRequest(
                                        "FREE", "신고 대상 글", "본문", false, null, List.of()),
                                authorUser,
                                "127.0.0.1")
                        .publicId();
        entityManager.flush();
    }

    private Member saveMember(String name, Role role) {
        return memberRepository.saveAndFlush(
                Member.builder()
                        .email(name + "@moderation.test")
                        .password("encoded")
                        .nickname(name)
                        .role(role)
                        .build());
    }

    private void reportPost(CustomUserDetails user) {
        reportService.create(
                new ContentReportCreateRequest(
                        ReportTargetType.POST,
                        postPublicId,
                        ReportReason.RIGHTS_INFRINGEMENT,
                        "명예훼손입니다"),
                user);
    }

    private AdminContentReportResponse pendingReport() {
        return moderationService.list(ReportStatus.PENDING, 0).reports().get(0);
    }

    private void apply(Long reportId, ModerationActionType action) {
        moderationService.apply(
                reportId, new ModerationActionRequest(action, 30, "메모"), admin.getEmail());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("같은 회원이 같은 글을 두 번 신고하면 거부한다")
    void report_Duplicate_Rejected() {
        reportPost(reporterUser);

        assertThatThrownBy(() -> reportPost(reporterUser))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_REPORT_DUPLICATE);
    }

    @Test
    @DisplayName("없는 글은 신고할 수 없다")
    void report_MissingTarget_Rejected() {
        assertThatThrownBy(
                        () ->
                                reportService.create(
                                        new ContentReportCreateRequest(
                                                ReportTargetType.POST,
                                                "missing",
                                                ReportReason.OTHER,
                                                null),
                                        reporterUser))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND);
    }

    @Test
    @DisplayName("임시조치하면 일반 사용자에게 안내 오류를 주고 관리자는 볼 수 있으며 양쪽에 알린다")
    void hold_Post_HidesAndNotifiesBothParties() {
        reportPost(reporterUser);
        apply(pendingReport().reportId(), ModerationActionType.TEMPORARY_HOLD);

        Post post = postRepository.findByPublicId(postPublicId).orElseThrow();
        assertThat(post.getStatus()).isEqualTo(PostStatus.TEMPORARY_HOLD);
        assertThatThrownBy(() -> postService.getPostDetail(postPublicId, reporterUser))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_TEMPORARILY_HELD);
        assertThat(postService.getPostDetail(postPublicId, new CustomUserDetails(admin)))
                .isNotNull();

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications)
                .filteredOn(item -> item.getType() == NotificationType.MODERATION_RESULT)
                .extracting(item -> item.getReceiver().getId())
                .containsExactlyInAnyOrder(author.getId(), reporter.getId());
        assertThat(notifications)
                .filteredOn(item -> item.getReceiver().getId().equals(author.getId()))
                .allSatisfy(item -> assertThat(item.getPreview()).doesNotContain("reporter"));
        assertThat(moderationService.list(ReportStatus.PENDING, 0).reports()).isEmpty();
    }

    @Test
    @DisplayName("임시조치된 글에 다시 임시조치할 수 없고 복구하면 다시 보인다")
    void restore_Post_BecomesVisible() {
        reportPost(reporterUser);
        Long reportId = pendingReport().reportId();
        apply(reportId, ModerationActionType.TEMPORARY_HOLD);

        assertThatThrownBy(() -> apply(reportId, ModerationActionType.TEMPORARY_HOLD))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_REPORT_ACTION_INVALID);

        apply(reportId, ModerationActionType.RESTORE);

        assertThat(postService.getPostDetail(postPublicId, reporterUser)).isNotNull();
    }

    @Test
    @DisplayName("기간이 지난 임시조치는 자동으로 복구되고 다시 처리되지 않는다")
    void releaseExpiredHolds_RestoresOnce() {
        reportPost(reporterUser);
        apply(pendingReport().reportId(), ModerationActionType.TEMPORARY_HOLD);
        ContentModerationAction hold =
                actionRepository.findAll().stream()
                        .filter(
                                action ->
                                        action.getActionType()
                                                == ModerationActionType.TEMPORARY_HOLD)
                        .findFirst()
                        .orElseThrow();
        assertThat(hold.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        entityManager
                .createQuery(
                        "UPDATE ContentModerationAction a SET a.expiresAt = :past WHERE a.id = :id")
                .setParameter("past", LocalDateTime.now().minusMinutes(1))
                .setParameter("id", hold.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(moderationService.releaseExpiredHolds()).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findByPublicId(postPublicId).orElseThrow().getStatus())
                .isEqualTo(PostStatus.NORMAL);
        assertThat(moderationService.releaseExpiredHolds()).isZero();
    }

    @Test
    @DisplayName("댓글 임시조치는 안내 문구로 가리고 복구하면 원문이 돌아온다")
    void hold_Comment_ShowsNoticeThenRestores() {
        CommentResponse comment =
                commentService.createComment(
                        postPublicId,
                        new CommentCreateRequest(null, "문제 댓글", false, null),
                        authorUser,
                        "127.0.0.1");
        reportService.create(
                new ContentReportCreateRequest(
                        ReportTargetType.COMMENT,
                        String.valueOf(comment.commentId()),
                        ReportReason.RIGHTS_INFRINGEMENT,
                        null),
                reporterUser);
        AdminContentReportResponse report = pendingReport();
        assertThat(report.targetState()).isEqualTo(TargetState.NORMAL);

        apply(report.reportId(), ModerationActionType.TEMPORARY_HOLD);

        PostCommentListResponse held = commentService.getCommentsByPost(postPublicId);
        assertThat(held.comments().get(0).content()).isEqualTo(CommentResponse.HELD_TEXT);
        assertThat(held.comments().get(0).isDeleted()).isTrue();

        apply(report.reportId(), ModerationActionType.RESTORE);

        PostCommentListResponse restored = commentService.getCommentsByPost(postPublicId);
        assertThat(restored.comments().get(0).content()).isEqualTo("문제 댓글");
    }

    @Test
    @DisplayName("작성자가 지운 글의 신고는 기각으로 정리할 수 있고 다른 조치는 거부한다")
    void deletedPost_OnlyDismissAllowed() {
        reportPost(reporterUser);
        Long reportId = pendingReport().reportId();
        postService.deletePost(postPublicId, null, authorUser);
        entityManager.flush();
        entityManager.clear();

        assertThat(pendingReport().targetState()).isEqualTo(TargetState.DELETED);
        assertThatThrownBy(() -> apply(reportId, ModerationActionType.TEMPORARY_HOLD))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_REPORT_ACTION_INVALID);

        apply(reportId, ModerationActionType.DISMISS);

        assertThat(moderationService.list(ReportStatus.PENDING, 0).reports()).isEmpty();
    }

    @Test
    @DisplayName("기각하면 글은 그대로이고 신고는 처리 완료가 된다")
    void dismiss_KeepsPostVisible() {
        reportPost(reporterUser);
        apply(pendingReport().reportId(), ModerationActionType.DISMISS);

        assertThat(postService.getPostDetail(postPublicId, reporterUser)).isNotNull();
        assertThat(moderationService.list(ReportStatus.RESOLVED, 0).reports()).hasSize(1);
    }
}
