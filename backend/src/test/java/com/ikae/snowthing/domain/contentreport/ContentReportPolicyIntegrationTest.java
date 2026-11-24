package com.ikae.snowthing.domain.contentreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 신고와 임시조치의 권한, 입력 경계, 상태 규칙 (IKA-118).
 *
 * <p>기준 문서: docs/conception/025_content_report/01_design.md. 기존 {@code
 * ContentModerationIntegrationTest}가 서비스 흐름을 다루므로 여기서는 HTTP 계약(상태 코드, 권한, 입력 검증)과 문서에 적힌 숫자(임시조치
 * 1~30일, 만료 시각은 2시간 여유)를 고정한다.
 *
 * <p>테스트 트랜잭션 안에서 돌고 끝나면 되돌린다. 다른 테스트가 남긴 행과 섞이지 않게 이 테스트가 만든 대상 ID로만 센다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentReportPolicyIntegrationTest {

    private static final String REPORTS = "/api/v1/reports";
    private static final String ADMIN_REPORTS = "/api/v1/admin/reports";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;

    private Member author;
    private Member reporter;
    private Member secondReporter;
    private Member admin;
    private String postPublicId;
    private Long postId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        author = saveMember(unique, "w", Role.ROLE_USER);
        reporter = saveMember(unique, "r", Role.ROLE_USER);
        secondReporter = saveMember(unique, "s", Role.ROLE_USER);
        admin = saveMember(unique, "a", Role.ROLE_ADMIN);
        categoryRepository
                .findByCode("FREE")
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder().name("자유게시판").code("FREE").build()));
        postPublicId = createPost("신고 대상 글");
        postId = postIdOf(postPublicId);
    }

    @Nested
    @DisplayName("신고 접수")
    class Create {

        @Test
        @DisplayName("입력이 규칙을 벗어나면 400이고 신고는 남지 않는다")
        void rejectsInvalidInput() throws Exception {
            List<Map<String, Object>> invalid =
                    List.of(
                            reportBody(null, postPublicId, "SPAM", null),
                            reportBody("POST", null, "SPAM", null),
                            reportBody("POST", " ", "SPAM", null),
                            reportBody("POST", "a".repeat(37), "SPAM", null),
                            reportBody("POST", postPublicId, null, null),
                            reportBody("POST", postPublicId, "SPAM", "가".repeat(501)));
            for (Map<String, Object> body : invalid) {
                report(reporter, body).andExpect(status().isBadRequest());
            }
            // 모르는 대상 종류와 사유는 본문 해석 단계에서 막힌다. MESSAGE는 IKA-123부터 받는 종류라 쓰지 않는다.
            report(reporter, reportBody("PROFILE", postPublicId, "SPAM", null))
                    .andExpect(status().isBadRequest());
            report(reporter, reportBody("POST", postPublicId, "BORING", null))
                    .andExpect(status().isBadRequest());

            assertThat(reportCount("POST", postId)).isZero();
        }

        @Test
        @DisplayName("상세 내용 500자는 받고, 앞뒤 공백은 지우며, 공백만 있으면 비운다")
        void detailBoundary() throws Exception {
            report(reporter, reportBody("POST", postPublicId, "OTHER", " " + "가".repeat(498) + " "))
                    .andExpect(status().isNoContent());
            report(secondReporter, reportBody("POST", postPublicId, "OTHER", "   "))
                    .andExpect(status().isNoContent());
            entityManager.flush();

            assertThat(
                            jdbc.queryForObject(
                                    "SELECT CHAR_LENGTH(detail) FROM content_report"
                                            + " WHERE target_id = ? AND reporter_id = ?",
                                    Integer.class,
                                    postId,
                                    reporter.getId()))
                    .isEqualTo(498);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT detail FROM content_report"
                                            + " WHERE target_id = ? AND reporter_id = ?",
                                    String.class,
                                    postId,
                                    secondReporter.getId()))
                    .isNull();
        }

        @Test
        @DisplayName("같은 회원의 중복 신고는 409지만 다른 회원은 같은 글을 따로 신고할 수 있다")
        void duplicateIsPerReporter() throws Exception {
            report(reporter, reportBody("POST", postPublicId, "SPAM", null))
                    .andExpect(status().isNoContent());
            report(reporter, reportBody("POST", postPublicId, "FRAUD", "사유를 바꿔도 같다"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTENT_REPORT_002"));
            report(secondReporter, reportBody("POST", postPublicId, "SPAM", null))
                    .andExpect(status().isNoContent());

            assertThat(reportCount("POST", postId)).isEqualTo(2);
        }

        @Test
        @DisplayName("댓글 신고: 중복은 409, 숫자가 아닌 ID와 없는 댓글과 삭제된 댓글은 404")
        void commentReports() throws Exception {
            Long commentId = createComment("신고할 댓글");
            Long deletedCommentId = createComment("지워질 댓글");
            jdbc.update(
                    "UPDATE comment SET is_deleted = true WHERE comment_id = ?", deletedCommentId);
            entityManager.clear();

            report(reporter, reportBody("COMMENT", commentId.toString(), "SPAM", null))
                    .andExpect(status().isNoContent());
            report(reporter, reportBody("COMMENT", commentId.toString(), "SPAM", null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTENT_REPORT_002"));
            for (String targetId :
                    List.of("abc", "1 OR 1=1", "999999999999", deletedCommentId.toString())) {
                report(reporter, reportBody("COMMENT", targetId, "SPAM", null))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("CONTENT_REPORT_001"));
            }
            // 글 신고와 댓글 신고는 대상이 다르므로 같은 회원이 둘 다 낼 수 있다.
            report(reporter, reportBody("POST", postPublicId, "SPAM", null))
                    .andExpect(status().isNoContent());

            assertThat(reportCount("COMMENT", commentId)).isEqualTo(1);
            assertThat(reportCount("COMMENT", deletedCommentId)).isZero();
        }
    }

    @Nested
    @DisplayName("관리자 권한")
    class AdminAccess {

        @Test
        @DisplayName("신고 목록과 조치는 비로그인 401, 일반 회원 403이고 대상 글은 그대로다")
        void onlyAdminsCanListAndAct() throws Exception {
            Long reportId = reportPost(reporter);
            String action = "{\"action\":\"DELETE\"}";

            mockMvc.perform(get(ADMIN_REPORTS)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(ADMIN_REPORTS).with(user(details(reporter))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(
                            post(ADMIN_REPORTS + "/{id}/actions", reportId)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(action))
                    .andExpect(status().isUnauthorized());
            for (Member member : List.of(reporter, author)) {
                mockMvc.perform(
                                post(ADMIN_REPORTS + "/{id}/actions", reportId)
                                        .with(csrf())
                                        .with(user(details(member)))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(action))
                        .andExpect(status().isForbidden());
            }
            // 관리자라도 CSRF 토큰이 없으면 거부한다.
            mockMvc.perform(
                            post(ADMIN_REPORTS + "/{id}/actions", reportId)
                                    .with(user(details(admin)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(action))
                    .andExpect(status().isForbidden());

            assertThat(postStatus()).isEqualTo("NORMAL");
            assertThat(reportStatus(reportId)).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("목록은 음수 쪽을 400으로 막고, 비회원 신고자의 IP는 가려서 보여 준다")
        void listMasksGuestIp() throws Exception {
            mockMvc.perform(
                            post(REPORTS)
                                    .with(csrf())
                                    .with(
                                            request -> {
                                                request.setRemoteAddr("203.0.113.77");
                                                return request;
                                            })
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(write(reportBody("POST", postPublicId, "SPAM", null))))
                    .andExpect(status().isNoContent());
            entityManager.flush();

            mockMvc.perform(get(ADMIN_REPORTS).param("page", "-1").with(user(details(admin))))
                    .andExpect(status().isBadRequest());
            String list =
                    mockMvc.perform(get(ADMIN_REPORTS).with(user(details(admin))))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

            assertThat(list).contains("비회원 (203.0.***.***)").doesNotContain("203.0.113.77");
        }
    }

    @Nested
    @DisplayName("조치")
    class Action {

        @Test
        @DisplayName("임시조치 일수는 1~30일만 받는다. 0일, 31일, 모르는 조치, 500자 넘는 메모는 400")
        void validatesActionRequest() throws Exception {
            Long reportId = reportPost(reporter);

            for (String body :
                    List.of(
                            "{\"action\":\"TEMPORARY_HOLD\",\"holdDays\":0}",
                            "{\"action\":\"TEMPORARY_HOLD\",\"holdDays\":31}",
                            "{\"action\":\"TEMPORARY_HOLD\",\"holdDays\":-1}",
                            "{\"action\":\"BAN\"}",
                            "{}",
                            "{\"action\":\"DISMISS\",\"memo\":\"" + "가".repeat(501) + "\"}")) {
                act(reportId, body).andExpect(status().isBadRequest());
            }

            assertThat(postStatus()).isEqualTo("NORMAL");
            assertThat(reportStatus(reportId)).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("임시조치 만료 시각은 요청한 일수에서 2시간을 뺀 때다. 1일이면 22시간 뒤")
        void holdExpiryLeavesTwoHourMargin() throws Exception {
            Long reportId = reportPost(reporter);
            LocalDateTime before = LocalDateTime.now();

            act(reportId, "{\"action\":\"TEMPORARY_HOLD\",\"holdDays\":1}")
                    .andExpect(status().isNoContent());
            entityManager.flush();
            LocalDateTime after = LocalDateTime.now();

            LocalDateTime expiresAt = holdExpiresAt();
            assertThat(expiresAt)
                    .isAfterOrEqualTo(before.plusHours(22).minusSeconds(1))
                    .isBeforeOrEqualTo(after.plusHours(22).plusSeconds(1));
            assertThat(postStatus()).isEqualTo("TEMPORARY_HOLD");
        }

        @Test
        @DisplayName("일수를 비우면 법정 상한 30일에서 2시간을 뺀 때에 만료되어 30일을 넘지 않는다")
        void holdDefaultsToLegalMaximum() throws Exception {
            Long reportId = reportPost(reporter);
            LocalDateTime before = LocalDateTime.now();

            act(reportId, "{\"action\":\"TEMPORARY_HOLD\"}").andExpect(status().isNoContent());
            entityManager.flush();

            LocalDateTime expiresAt = holdExpiresAt();
            assertThat(expiresAt)
                    .isAfterOrEqualTo(before.plusDays(30).minusHours(2).minusSeconds(1))
                    .isBefore(before.plusDays(30));
        }

        @Test
        @DisplayName("한 번의 조치로 같은 대상의 대기 신고가 모두 처리되고 다른 글의 신고는 남는다")
        void resolvesEveryPendingReportOfTheTarget() throws Exception {
            Long first = reportPost(reporter);
            Long second = reportPost(secondReporter);
            String otherPost = createPost("다른 글");
            report(reporter, reportBody("POST", otherPost, "SPAM", null))
                    .andExpect(status().isNoContent());
            entityManager.flush();
            Long otherReport = latestReportId(postIdOf(otherPost));

            act(first, "{\"action\":\"DISMISS\",\"memo\":\"문제 없음\"}")
                    .andExpect(status().isNoContent());
            entityManager.flush();

            assertThat(reportStatus(first)).isEqualTo("RESOLVED");
            assertThat(reportStatus(second)).isEqualTo("RESOLVED");
            assertThat(reportStatus(otherReport)).isEqualTo("PENDING");
            assertThat(postStatus()).isEqualTo("NORMAL");
        }

        @Test
        @DisplayName("기각은 신고자에게만 알리고 작성자에게는 알리지 않는다. 알림에 신고자 정보가 없다")
        void dismissNotifiesReportersOnly() throws Exception {
            Long reportId = reportPost(reporter);
            reportPost(secondReporter);

            act(reportId, "{\"action\":\"DISMISS\"}").andExpect(status().isNoContent());
            entityManager.flush();

            assertThat(moderationNotificationCount(reporter)).isEqualTo(1);
            assertThat(moderationNotificationCount(secondReporter)).isEqualTo(1);
            assertThat(moderationNotificationCount(author)).isZero();
        }

        @Test
        @DisplayName("임시조치 알림은 작성자와 신고자에게 하나씩 가고, 작성자 알림에 신고자 닉네임이 없다")
        void holdNotificationHidesReporter() throws Exception {
            Long reportId = reportPost(reporter);

            act(reportId, "{\"action\":\"TEMPORARY_HOLD\",\"holdDays\":7}")
                    .andExpect(status().isNoContent());
            entityManager.flush();

            assertThat(moderationNotificationCount(reporter)).isEqualTo(1);
            assertThat(moderationNotificationCount(author)).isEqualTo(1);
            Map<String, Object> authorNotification =
                    jdbc.queryForMap(
                            "SELECT * FROM notification WHERE receiver_id = ?"
                                    + " AND type = 'MODERATION_RESULT'",
                            author.getId());
            assertThat(authorNotification.values())
                    .allSatisfy(
                            value ->
                                    assertThat(String.valueOf(value))
                                            .doesNotContain(reporter.getNickname())
                                            .doesNotContain(reporter.getEmail()));
        }

        @Test
        @DisplayName("상태에 맞지 않는 조치는 409: 정상 글 복구, 임시조치 중 기각과 재임시조치, 삭제 뒤 삭제")
        void rejectsActionsThatDoNotFitTheState() throws Exception {
            Long reportId = reportPost(reporter);

            act(reportId, "{\"action\":\"RESTORE\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTENT_REPORT_004"));

            act(reportId, "{\"action\":\"TEMPORARY_HOLD\",\"holdDays\":3}")
                    .andExpect(status().isNoContent());
            entityManager.flush();
            entityManager.clear();
            act(reportId, "{\"action\":\"DISMISS\"}").andExpect(status().isConflict());
            act(reportId, "{\"action\":\"TEMPORARY_HOLD\",\"holdDays\":3}")
                    .andExpect(status().isConflict());
            assertThat(postStatus()).isEqualTo("TEMPORARY_HOLD");

            act(reportId, "{\"action\":\"DELETE\"}").andExpect(status().isNoContent());
            entityManager.flush();
            entityManager.clear();
            act(reportId, "{\"action\":\"DELETE\"}").andExpect(status().isConflict());
            act(reportId, "{\"action\":\"RESTORE\"}").andExpect(status().isConflict());
            assertThat(postStatus()).isEqualTo("DELETED");
            // 삭제하면 걸려 있던 임시조치는 해제 시각이 남아 만료 복구 대상에서 빠진다.
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM content_moderation_action"
                                            + " WHERE target_id = ? AND action_type = 'TEMPORARY_HOLD'"
                                            + " AND released_at IS NULL",
                                    Integer.class,
                                    postId))
                    .isZero();
        }

        @Test
        @DisplayName("없는 신고 번호는 404다")
        void unknownReportIsNotFound() throws Exception {
            act(Long.MAX_VALUE, "{\"action\":\"DISMISS\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CONTENT_REPORT_003"));
        }
    }

    private Long reportPost(Member member) throws Exception {
        report(member, reportBody("POST", postPublicId, "RIGHTS_INFRINGEMENT", "명예훼손입니다"))
                .andExpect(status().isNoContent());
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT content_report_id FROM content_report"
                        + " WHERE target_type = 'POST' AND target_id = ? AND reporter_id = ?",
                Long.class,
                postId,
                member.getId());
    }

    private Long latestReportId(Long targetId) {
        return jdbc.queryForObject(
                "SELECT MAX(content_report_id) FROM content_report"
                        + " WHERE target_type = 'POST' AND target_id = ?",
                Long.class,
                targetId);
    }

    private ResultActions report(Member member, Map<String, Object> body) throws Exception {
        MockHttpServletRequestBuilder request =
                post(REPORTS)
                        .with(csrf())
                        .with(user(details(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(write(body));
        return mockMvc.perform(request);
    }

    private ResultActions act(Long reportId, String body) throws Exception {
        return mockMvc.perform(
                post(ADMIN_REPORTS + "/{id}/actions", reportId)
                        .with(csrf())
                        .with(user(details(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private static Map<String, Object> reportBody(
            String targetType, String targetId, String reason, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("reason", reason);
        body.put("detail", detail);
        return body;
    }

    private LocalDateTime holdExpiresAt() {
        return jdbc.queryForObject(
                        "SELECT expires_at FROM content_moderation_action"
                                + " WHERE target_type = 'POST' AND target_id = ?"
                                + " AND action_type = 'TEMPORARY_HOLD'",
                        Timestamp.class,
                        postId)
                .toLocalDateTime();
    }

    private int reportCount(String targetType, Long targetId) {
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_report WHERE target_type = ? AND target_id = ?",
                Integer.class,
                targetType,
                targetId);
    }

    private String reportStatus(Long reportId) {
        return jdbc.queryForObject(
                "SELECT status FROM content_report WHERE content_report_id = ?",
                String.class,
                reportId);
    }

    private String postStatus() {
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT status FROM post WHERE post_id = ?", String.class, postId);
    }

    private int moderationNotificationCount(Member receiver) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE receiver_id = ?"
                        + " AND type = 'MODERATION_RESULT'",
                Integer.class,
                receiver.getId());
    }

    private String createPost(String title) {
        String publicId =
                postService
                        .createPost(
                                new PostCreateRequest("FREE", title, "본문", false, null, List.of()),
                                details(author),
                                "127.0.0.1")
                        .publicId();
        entityManager.flush();
        return publicId;
    }

    private Long postIdOf(String publicId) {
        return jdbc.queryForObject(
                "SELECT post_id FROM post WHERE public_id = ?", Long.class, publicId);
    }

    private Long createComment(String content) {
        commentService.createComment(
                postPublicId,
                new CommentCreateRequest(null, content, false, null),
                details(author),
                "127.0.0.1");
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT MAX(comment_id) FROM comment WHERE post_id = ?", Long.class, postId);
    }

    private CustomUserDetails details(Member member) {
        return new CustomUserDetails(member);
    }

    private String write(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Member saveMember(String unique, String suffix, Role role) {
        return memberRepository.saveAndFlush(
                Member.builder()
                        .publicId(UUID.randomUUID().toString())
                        .email(unique + "-" + suffix + "@example.com")
                        .password("encoded")
                        .nickname((suffix + unique).substring(0, 12))
                        .role(role)
                        .build());
    }
}
