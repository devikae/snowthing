package com.ikae.snowthing.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.notification.entity.Notification;
import com.ikae.snowthing.domain.notification.entity.NotificationType;
import com.ikae.snowthing.domain.notification.repository.NotificationRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 실제 트랜잭션 커밋과 MySQL로 댓글 알림의 받는 사람 규칙, 커밋 의존성, 알림 API를 본다.
 *
 * <p>AFTER_COMMIT 리스너는 테스트 트랜잭션 안에서는 불리지 않으므로 클래스에 {@code @Transactional}을 두지 않고 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    private static final String IP = "127.0.0.1";

    @Autowired private MockMvc mockMvc;
    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<String> postIds = new ArrayList<>();

    private CustomUserDetails postAuthor;
    private CustomUserDetails commenter;
    private CustomUserDetails replier;

    @BeforeEach
    void setUp() {
        ensureCategory("FREE", "자유게시판");
        ensureCategory("INQUIRY", "1:1 문의");
        postAuthor = new CustomUserDetails(saveMember("글쓴이", Role.ROLE_USER));
        commenter = new CustomUserDetails(saveMember("댓글러", Role.ROLE_USER));
        replier = new CustomUserDetails(saveMember("답글러", Role.ROLE_USER));
    }

    @AfterEach
    void tearDown() {
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM notification WHERE receiver_id = ?", memberId);
        }
        for (String publicId : postIds) {
            Long postId =
                    jdbc.queryForObject(
                            "SELECT post_id FROM post WHERE public_id = ?", Long.class, publicId);
            jdbc.update("DELETE FROM comment WHERE post_id = ? AND parent_id IS NOT NULL", postId);
            jdbc.update("DELETE FROM comment WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post_content_image_reference WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("내 글에 댓글이 달리면 글 작성자에게 POST_COMMENT가 하나 생긴다")
    void postCommentNotifiesPostAuthor() {
        String post = createPost(postAuthor, "FREE");
        Long commentId = comment(post, null, commenter, "좋은 글이네요", false);

        List<Notification> notifications = notificationsOf(postAuthor);
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getType()).isEqualTo(NotificationType.POST_COMMENT);
        assertThat(notification.getCommentId()).isEqualTo(commentId);
        assertThat(notification.getPostPublicId()).isEqualTo(post);
        assertThat(notification.getActorName()).isEqualTo(commenter.getNickname());
        assertThat(notification.getPreview()).isEqualTo("좋은 글이네요");
        assertThat(notification.isRead()).isFalse();
        assertThat(notificationsOf(commenter)).isEmpty();
    }

    @Test
    @DisplayName("남의 글에 단 내 댓글에 답글이 달리면 댓글 작성자는 COMMENT_REPLY, 글 작성자는 POST_COMMENT를 받는다")
    void replyNotifiesParentAuthorAndPostAuthor() {
        String post = createPost(postAuthor, "FREE");
        Long root = comment(post, null, commenter, "루트 댓글", false);
        notificationRepository.deleteAll(notificationsOf(postAuthor));

        comment(post, root, replier, "답글입니다", false);

        assertThat(notificationsOf(commenter))
                .extracting(Notification::getType)
                .containsExactly(NotificationType.COMMENT_REPLY);
        assertThat(notificationsOf(postAuthor))
                .extracting(Notification::getType)
                .containsExactly(NotificationType.POST_COMMENT);
        assertThat(notificationsOf(replier)).isEmpty();
    }

    @Test
    @DisplayName("내 글의 내 댓글에 답글이 달리면 COMMENT_REPLY 하나만 받는다")
    void replyOnOwnCommentInOwnPostIsDeduplicated() {
        String post = createPost(postAuthor, "FREE");
        Long root = comment(post, null, postAuthor, "글쓴이 댓글", false);
        assertThat(notificationsOf(postAuthor)).isEmpty();

        comment(post, root, commenter, "답글", false);

        assertThat(notificationsOf(postAuthor))
                .extracting(Notification::getType)
                .containsExactly(NotificationType.COMMENT_REPLY);
    }

    @Test
    @DisplayName("회원이 익명으로 쓴 댓글과 비회원 댓글의 작성자는 익명으로 저장된다")
    void anonymousActorIsHidden() {
        String post = createPost(postAuthor, "FREE");
        comment(post, null, commenter, "익명 회원 댓글", true);
        commentService.createComment(
                post, new CommentCreateRequest(null, "비회원 댓글", true, "guest1234"), null, IP);

        assertThat(notificationsOf(postAuthor))
                .extracting(Notification::getActorName)
                .containsExactly("익명", "익명");
    }

    @Test
    @DisplayName("탈퇴 회원에게는 알림을 만들지 않고, 정지 회원에게는 만든다")
    void withdrawnReceiverIsSkipped() {
        String post = createPost(postAuthor, "FREE");
        Long root = comment(post, null, commenter, "루트", false);
        notificationRepository.deleteAll(notificationsOf(postAuthor));
        setStatus(postAuthor, MemberStatus.WITHDRAWN);
        setStatus(commenter, MemberStatus.SUSPENDED);

        comment(post, root, replier, "답글", false);

        assertThat(notificationsOf(postAuthor)).isEmpty();
        assertThat(notificationsOf(commenter))
                .extracting(Notification::getType)
                .containsExactly(NotificationType.COMMENT_REPLY);
    }

    @Test
    @DisplayName("비회원 익명 글에는 받는 사람이 없어 알림이 생기지 않는다")
    void guestPostHasNoReceiver() {
        String post =
                postService
                        .createPost(
                                new PostCreateRequest(
                                        "FREE", "비회원 글", "본문", true, "guest1234", List.of()),
                                null,
                                IP)
                        .publicId();
        postIds.add(post);

        Long commentId = comment(post, null, commenter, "댓글", false);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM notification WHERE comment_id = ?",
                                Long.class,
                                commentId))
                .isZero();
    }

    @Test
    @DisplayName("관리자가 1:1 문의에 답변하면 문의 작성자에게 INQUIRY_ANSWER 하나가 생긴다")
    void adminAnswerOnInquiry() {
        CustomUserDetails admin = new CustomUserDetails(saveMember("운영자", Role.ROLE_ADMIN));
        String inquiry = createPost(postAuthor, "INQUIRY");
        Long question = comment(inquiry, null, postAuthor, "추가 질문", false);

        comment(inquiry, question, admin, "답변드립니다", false);
        comment(inquiry, null, admin, "추가 안내", false);

        assertThat(notificationsOf(postAuthor))
                .extracting(Notification::getType)
                .containsExactly(NotificationType.INQUIRY_ANSWER, NotificationType.INQUIRY_ANSWER);
    }

    @Test
    @DisplayName("댓글 트랜잭션이 롤백되면 알림이 생기지 않는다")
    void rollbackCreatesNoNotification() {
        String post = createPost(postAuthor, "FREE");

        transactionTemplate.executeWithoutResult(
                status -> {
                    comment(post, null, commenter, "롤백될 댓글", false);
                    status.setRollbackOnly();
                });

        assertThat(notificationsOf(postAuthor)).isEmpty();
    }

    @Test
    @DisplayName("목록은 ID 내림차순 커서로 나뉘고 unreadOnly로 안 읽은 것만 본다")
    void listApiUsesDescendingCursor() throws Exception {
        String post = createPost(postAuthor, "FREE");
        for (int i = 0; i < 3; i++) {
            comment(post, null, commenter, "댓글 " + i, false);
        }
        List<Notification> all = notificationsOf(postAuthor);
        Long newest = all.get(0).getId();
        Long middle = all.get(1).getId();
        Long oldest = all.get(2).getId();
        assertThat(newest).isGreaterThan(middle);
        assertThat(middle).isGreaterThan(oldest);

        mockMvc.perform(get("/api/v1/notifications").param("size", "2").with(user(postAuthor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].notificationId").value(newest.toString()))
                .andExpect(jsonPath("$.items[0].type").value("POST_COMMENT"))
                .andExpect(jsonPath("$.items[0].postPublicId").value(post))
                .andExpect(jsonPath("$.items[0].postCategoryCode").value("FREE"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[1].notificationId").value(middle.toString()))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value(middle.toString()));
        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("size", "2")
                                .param("cursor", middle.toString())
                                .with(user(postAuthor)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].notificationId").value(oldest.toString()))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(
                        put("/api/v1/notifications/{id}/read", middle)
                                .with(csrf())
                                .with(user(postAuthor)))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        put("/api/v1/notifications/{id}/read", middle)
                                .with(csrf())
                                .with(user(postAuthor)))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("unreadOnly", "true")
                                .with(user(postAuthor)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].notificationId").value(newest.toString()))
                .andExpect(jsonPath("$.items[1].notificationId").value(oldest.toString()));
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(user(postAuthor)))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("남의 알림 읽음 처리는 404이고 모두 읽음은 내 알림만 바꾼다")
    void readApisAreScopedToReceiver() throws Exception {
        String post = createPost(postAuthor, "FREE");
        String otherPost = createPost(commenter, "FREE");
        comment(post, null, commenter, "댓글", false);
        comment(otherPost, null, postAuthor, "댓글", false);
        Long mine = notificationsOf(postAuthor).get(0).getId();

        mockMvc.perform(
                        put("/api/v1/notifications/{id}/read", mine)
                                .with(csrf())
                                .with(user(commenter)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_001"));

        mockMvc.perform(put("/api/v1/notifications/read-all").with(csrf()).with(user(postAuthor)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(user(postAuthor)))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(user(commenter)))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("비로그인은 알림 API 모두 401이다")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/notifications/1/read").with(csrf()).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/notifications/read-all").with(csrf()).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("90일 지난 알림만 지운다")
    void cleanupDeletesExpiredOnly() {
        String post = createPost(postAuthor, "FREE");
        comment(post, null, commenter, "오래된 알림", false);
        comment(post, null, commenter, "최근 알림", false);
        List<Notification> notifications = notificationsOf(postAuthor);
        Long recent = notifications.get(0).getId();
        Long old = notifications.get(1).getId();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                "UPDATE notification SET created_at = ? WHERE notification_id = ?",
                now.minusDays(91),
                old);
        jdbc.update(
                "UPDATE notification SET created_at = ? WHERE notification_id = ?",
                now.minusDays(89),
                recent);

        notificationRepository.deleteCreatedBefore(now.minusDays(90), 1_000);

        assertThat(notificationsOf(postAuthor))
                .extracting(Notification::getId)
                .containsExactly(recent);
    }

    private void ensureCategory(String code, String name) {
        categoryRepository
                .findByCode(code)
                .orElseGet(() -> categoryRepository.save(new PostCategory(name, code)));
    }

    private Member saveMember(String label, Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email("noti-" + suffix + "@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname(label + "-" + suffix)
                                .role(role)
                                .status(MemberStatus.ACTIVE)
                                .build());
        memberIds.add(member.getId());
        return member;
    }

    private void setStatus(CustomUserDetails userDetails, MemberStatus status) {
        jdbc.update(
                "UPDATE member SET status = ? WHERE member_id = ?",
                status.name(),
                userDetails.getMember().getId());
    }

    private String createPost(CustomUserDetails author, String category) {
        String publicId =
                postService
                        .createPost(
                                new PostCreateRequest(
                                        category, "알림 테스트", "본문", false, null, List.of()),
                                author,
                                IP)
                        .publicId();
        postIds.add(publicId);
        return publicId;
    }

    private Long comment(
            String postPublicId,
            Long parentId,
            CustomUserDetails author,
            String content,
            boolean anonymous) {
        return commentService
                .createComment(
                        postPublicId,
                        new CommentCreateRequest(parentId, content, anonymous, null),
                        author,
                        IP)
                .commentId();
    }

    private List<Notification> notificationsOf(CustomUserDetails receiver) {
        return notificationRepository.findPage(receiver.getMember().getId(), false, null, 100);
    }
}
