package com.ikae.snowthing.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;

/** 관리자 운영 기능 확장 통합 테스트 (IKA-78). 실제 로그인 세션으로 권한, 조회, 댓글 조치와 조치 기록을 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminOpsExtensionIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String COMMENTS = "/api/v1/admin/comments";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberRepository members;
    @Autowired private PostRepository posts;
    @Autowired private PostCategoryRepository categories;
    @Autowired private CommentRepository comments;

    private final List<Long> memberIds = new ArrayList<>();
    private Member admin;
    private Member user;
    private MockHttpSession adminSession;
    private MockHttpSession userSession;

    @BeforeEach
    void setUp() throws Exception {
        admin = member("adm", Role.ROLE_ADMIN);
        user = member("usr", Role.ROLE_USER);
        adminSession = login(admin);
        userSession = login(user);
    }

    @AfterEach
    void tearDown() {
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM admin_action_log WHERE actor_member_id = ?", memberId);
            jdbc.update(
                    "DELETE FROM comment WHERE post_id IN (SELECT post_id FROM post WHERE member_id = ?)",
                    memberId);
            jdbc.update("DELETE FROM comment WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM post WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("새 관리자 조회 API는 일반 회원 403, 비로그인 401, 관리자 200이다")
    void newReadApisAreAdminOnly() throws Exception {
        List<String> reads =
                List.of(
                        "/api/v1/admin/dashboard/overview",
                        "/api/v1/admin/members/" + user.getPublicId(),
                        COMMENTS,
                        "/api/v1/admin/inquiries",
                        "/api/v1/admin/resort-reports",
                        "/api/v1/admin/action-logs",
                        "/api/v1/admin/posts?category=CARPOOL&filter=HIDDEN");
        for (String url : reads) {
            mockMvc.perform(get(url).session(userSession)).andExpect(status().isForbidden());
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(url).session(adminSession)).andExpect(status().isOk());
        }
        Comment comment = saveComment(savePost("FREE", user), user);
        mockMvc.perform(blind(comment.getId(), true).session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(COMMENTS + "/" + comment.getId()).session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(heldOf(comment.getId())).isFalse();
    }

    @Test
    @DisplayName("대시보드는 오늘을 마지막으로 7일, 30일 일별 건수를 주고 다른 기간은 400이다")
    void overviewSeries() throws Exception {
        saveComment(savePost("FREE", user), user);

        JsonNode week = read("/api/v1/admin/dashboard/overview?days=7");
        assertThat(week.get("series")).hasSize(7);
        JsonNode today = week.get("today");
        // CI의 JVM 시간대가 KST가 아닐 수 있어 오늘 칸 대신 7일 합으로 확인한다.
        assertThat(sum(week, "signups")).isGreaterThanOrEqualTo(2);
        assertThat(sum(week, "posts")).isGreaterThanOrEqualTo(1);
        assertThat(sum(week, "comments")).isGreaterThanOrEqualTo(1);
        assertThat(week.get("series").get(6).get("date").asText())
                .isEqualTo(today.get("date").asText());
        assertThat(week.has("pendingReports")).isTrue();
        assertThat(week.has("unansweredInquiries")).isTrue();

        assertThat(read("/api/v1/admin/dashboard/overview?days=30").get("series")).hasSize(30);
        mockMvc.perform(get("/api/v1/admin/dashboard/overview?days=10").session(adminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("댓글 블라인드, 해제, 삭제는 상태를 바꾸고 조치 기록을 같은 관리자로 남긴다")
    void commentModerationWritesActionLog() throws Exception {
        Comment comment = saveComment(savePost("FREE", user), user);
        Long id = comment.getId();

        mockMvc.perform(blind(id, true).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(heldOf(id)).isTrue();
        assertThat(commentIds("HELD")).contains(id);
        // 같은 요청은 그대로 성공하고 기록을 더 남기지 않는다.
        mockMvc.perform(blind(id, true).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(blind(id, false).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(heldOf(id)).isFalse();

        mockMvc.perform(delete(COMMENTS + "/" + id).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(commentIds("DELETED")).contains(id);
        mockMvc.perform(delete(COMMENTS + "/" + id).session(adminSession).with(csrf()))
                .andExpect(status().isNotFound());

        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT action, actor_member_id FROM admin_action_log"
                                + " WHERE target_type = 'COMMENT' AND target_id = ?"
                                + " ORDER BY admin_action_log_id",
                        String.valueOf(id));
        assertThat(rows)
                .extracting(row -> row.get("action"))
                .containsExactly("COMMENT_BLIND", "COMMENT_UNBLIND", "COMMENT_DELETE");
        assertThat(rows)
                .allSatisfy(
                        row ->
                                assertThat(((Number) row.get("actor_member_id")).longValue())
                                        .isEqualTo(admin.getId()));

        JsonNode logs = read("/api/v1/admin/action-logs?targetType=COMMENT&targetId=" + id);
        assertThat(logs.get("items")).hasSize(3);
        assertThat(logs.get("items").get(0).get("action").asText()).isEqualTo("COMMENT_DELETE");
        assertThat(logs.get("items").get(0).get("actorPublicId").asText())
                .isEqualTo(admin.getPublicId());
        mockMvc.perform(get("/api/v1/admin/action-logs?action=NOPE").session(adminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("댓글은 내용과 작성자로 찾고 ID 역순이다")
    void commentSearch() throws Exception {
        Post post = savePost("FREE", user);
        String keyword = "kw" + UUID.randomUUID().toString().substring(0, 8);
        Comment first = comments.save(comment(post, user, keyword + " 첫째"));
        Comment second = comments.save(comment(post, user, keyword + " 둘째"));

        JsonNode byContent = read(COMMENTS + "?query=" + keyword);
        assertThat(byContent.get("items")).hasSize(2);
        assertThat(byContent.get("items").get(0).get("commentId").asLong())
                .isEqualTo(second.getId());
        assertThat(byContent.get("items").get(1).get("commentId").asLong())
                .isEqualTo(first.getId());
        JsonNode byAuthor = read(COMMENTS + "?author=" + user.getNickname() + "&size=1");
        assertThat(byAuthor.get("items")).hasSize(1);
        assertThat(byAuthor.get("hasNext").asBoolean()).isTrue();
        mockMvc.perform(get(COMMENTS + "?filter=WRONG").session(adminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원 상세는 이메일을 가리고 비밀번호를 내보내지 않으며 정지 이력을 보여 준다")
    void memberDetail() throws Exception {
        saveComment(savePost("FREE", user), user);
        mockMvc.perform(
                        post("/api/v1/admin/members/{id}/suspend", user.getPublicId())
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isOk());

        MvcResult result =
                mockMvc.perform(
                                get("/api/v1/admin/members/{id}", user.getPublicId())
                                        .session(adminSession))
                        .andExpect(status().isOk())
                        .andReturn();
        String raw = result.getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(raw);
        assertThat(raw).doesNotContain(user.getEmail()).doesNotContain(user.getPassword());
        assertThat(body.get("maskedEmail").asText()).startsWith("us***@");
        assertThat(body.get("hasPassword").asBoolean()).isTrue();
        assertThat(body.get("postCount").asLong()).isEqualTo(1);
        assertThat(body.get("commentCount").asLong()).isEqualTo(1);
        assertThat(body.get("recentPosts")).hasSize(1);
        assertThat(body.get("recentComments")).hasSize(1);
        assertThat(body.get("status").asText()).isEqualTo("SUSPENDED");
        assertThat(body.get("history").get(0).get("action").asText()).isEqualTo("MEMBER_SUSPEND");
        mockMvc.perform(
                        get("/api/v1/admin/members/{id}", UUID.randomUUID().toString())
                                .session(adminSession))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("1:1 문의는 관리자 댓글이 있으면 답변 완료, 없으면 답변 대기다")
    void inquiryAnsweredStatus() throws Exception {
        Post waiting = savePost("INQUIRY", user);
        Post answered = savePost("INQUIRY", user);
        saveComment(answered, admin);

        List<String> unanswered = inquiryIds("UNANSWERED");
        List<String> done = inquiryIds("ANSWERED");
        assertThat(unanswered)
                .contains(waiting.getPublicId())
                .doesNotContain(answered.getPublicId());
        assertThat(done).contains(answered.getPublicId()).doesNotContain(waiting.getPublicId());
        assertThat(read("/api/v1/admin/dashboard/overview").get("unansweredInquiries").asLong())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("게시글 관리 목록은 게시판 코드로 거른다")
    void postListFiltersByCategory() throws Exception {
        Post carpool = savePost("CARPOOL", user);
        Post free = savePost("FREE", user);

        JsonNode content = read("/api/v1/admin/posts?category=CARPOOL&size=50").get("content");
        List<String> ids = new ArrayList<>();
        content.forEach(item -> ids.add(item.get("publicId").asText()));
        assertThat(ids).contains(carpool.getPublicId()).doesNotContain(free.getPublicId());
    }

    private static long sum(JsonNode overview, String field) {
        long total = 0;
        for (JsonNode day : overview.get("series")) {
            total += day.get(field).asLong();
        }
        return total;
    }

    private MockHttpServletRequestBuilder blind(Long commentId, boolean blinded) {
        return patch(COMMENTS + "/" + commentId + "/blind")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blinded\":" + blinded + "}");
    }

    private List<Long> commentIds(String filter) throws Exception {
        List<Long> ids = new ArrayList<>();
        read(COMMENTS + "?size=50&filter=" + filter)
                .get("items")
                .forEach(item -> ids.add(item.get("commentId").asLong()));
        return ids;
    }

    private List<String> inquiryIds(String status) throws Exception {
        List<String> ids = new ArrayList<>();
        read("/api/v1/admin/inquiries?size=50&status=" + status)
                .get("items")
                .forEach(item -> ids.add(item.get("publicId").asText()));
        return ids;
    }

    private JsonNode read(String url) throws Exception {
        MvcResult result =
                mockMvc.perform(get(url).session(adminSession))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private boolean heldOf(Long commentId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT moderation_held FROM comment WHERE comment_id = ?",
                        Boolean.class,
                        commentId));
    }

    private Post savePost(String code, Member writer) {
        PostCategory category =
                categories
                        .findByCode(code)
                        .orElseGet(
                                () ->
                                        categories.save(
                                                PostCategory.builder()
                                                        .code(code)
                                                        .name(code)
                                                        .build()));
        return posts.save(
                Post.builder()
                        .member(writer)
                        .category(category)
                        .title("관리자 확장 테스트")
                        .content("본문")
                        .writerIp("192.0.2.1")
                        .isAnonymous(false)
                        .build());
    }

    private Comment saveComment(Post post, Member writer) {
        return comments.save(comment(post, writer, "댓글 본문"));
    }

    private static Comment comment(Post post, Member writer, String content) {
        return Comment.builder()
                .post(post)
                .member(writer)
                .content(content)
                .writerIp("192.0.2.1")
                .isAnonymous(false)
                .build();
    }

    private MockHttpSession login(Member member) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                member.getEmail(),
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Member member(String prefix, Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member saved =
                members.save(
                        Member.builder()
                                .email(prefix + "-" + unique + "@snowthing.com")
                                .password(passwordEncoder.encode(PASSWORD))
                                .nickname(prefix + unique)
                                .role(role)
                                .status(MemberStatus.ACTIVE)
                                .build());
        memberIds.add(saved.getId());
        return saved;
    }
}
