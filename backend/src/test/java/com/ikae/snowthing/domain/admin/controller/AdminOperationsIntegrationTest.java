package com.ikae.snowthing.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import com.ikae.snowthing.domain.admin.service.ChatIpBanService;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.dto.SenderDto;
import com.ikae.snowthing.domain.chat.service.ChatRecentHistoryBuffer;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;

/** 관리자 운영 제어 API 통합 테스트 (IKA-68). 실제 로그인 세션으로 권한, CSRF, 게시글 블라인드와 삭제 기록을 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminOperationsIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String POSTS = "/api/v1/admin/posts";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberRepository members;
    @Autowired private PostRepository posts;
    @Autowired private PostCategoryRepository categories;
    @Autowired private CommentRepository comments;
    @Autowired private ChatRecentHistoryBuffer chatHistory;
    @Autowired private ChatIpBanService chatIpBanService;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<String> bannedIps = new ArrayList<>();
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
        for (String ip : bannedIps) {
            for (Long id :
                    jdbc.queryForList(
                            "SELECT id FROM chat_banned_ip WHERE banned_ip = ?", Long.class, ip)) {
                chatIpBanService.unbanIp(id);
            }
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("관리자 운영 API는 일반 회원 403, 비로그인 401이다")
    void adminApisAreClosedToNonAdmins() throws Exception {
        List<String> reads =
                List.of(
                        "/api/v1/admin/dashboard/stats",
                        POSTS,
                        "/api/v1/admin/configs",
                        "/api/v1/admin/chat/banned-ips",
                        "/api/v1/admin/banners");
        for (String url : reads) {
            mockMvc.perform(get(url).session(userSession)).andExpect(status().isForbidden());
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(url).session(adminSession)).andExpect(status().isOk());
        }

        String publicId = savePost().getPublicId();
        mockMvc.perform(blind(publicId, true).session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(POSTS + "/" + publicId).session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        patch("/api/v1/admin/configs/CHAT_ANONYMOUS_ENABLED")
                                .session(userSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"value\":\"true\"}"))
                .andExpect(status().isForbidden());
        assertThat(statusOf(publicId)).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 관리자 변경 요청도 403이다")
    void adminMutationsRequireCsrf() throws Exception {
        String publicId = savePost().getPublicId();

        mockMvc.perform(blind(publicId, true).session(adminSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/api/v1/admin/chat/banned-ips")
                                .session(adminSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bannedIp\":\"192.0.2.9\",\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());

        assertThat(statusOf(publicId)).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("블라인드한 글은 일반 조회에서 404, 관리자 목록에는 남고, 해제하면 다시 보인다")
    void blindHidesFromPublicAndCanBeUndone() throws Exception {
        String publicId = savePost().getPublicId();

        mockMvc.perform(blind(publicId, true).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(statusOf(publicId)).isEqualTo("BLOCKED");
        mockMvc.perform(get("/api/v1/posts/" + publicId)).andExpect(status().isNotFound());
        assertThat(adminList("BLINDED")).contains(publicId);
        assertThat(adminList("DELETED")).doesNotContain(publicId);

        // 같은 요청을 다시 보내도 상태가 그대로다.
        mockMvc.perform(blind(publicId, true).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(blind(publicId, false).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(statusOf(publicId)).isEqualTo("NORMAL");
        mockMvc.perform(get("/api/v1/posts/" + publicId)).andExpect(status().isOk());
        assertThat(adminList("BLINDED")).doesNotContain(publicId);
    }

    @Test
    @DisplayName("신고 임시조치 중인 글은 블라인드로 바꾸지 못한다")
    void temporaryHoldCannotBeBlinded() throws Exception {
        String publicId = savePost().getPublicId();
        jdbc.update("UPDATE post SET status = 'TEMPORARY_HOLD' WHERE public_id = ?", publicId);

        mockMvc.perform(blind(publicId, true).session(adminSession).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(blind(publicId, false).session(adminSession).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(statusOf(publicId)).isEqualTo(PostStatus.TEMPORARY_HOLD.name());
    }

    @Test
    @DisplayName("IP 차단 입력이 컬럼 길이를 넘으면 400이다")
    void banIpRejectsTooLongInput() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/chat/banned-ips")
                                .session(adminSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"bannedIp\":\""
                                                + "1".repeat(46)
                                                + "\",\"reason\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 글을 블라인드하면 404다")
    void blindUnknownPost() throws Exception {
        mockMvc.perform(
                        blind(UUID.randomUUID().toString(), true)
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자 삭제는 삭제한 관리자를 남기고, 삭제 목록에서 작성자 삭제와 구분된다")
    void adminDeleteRecordsDeleter() throws Exception {
        String publicId = savePost().getPublicId();

        mockMvc.perform(delete(POSTS + "/" + publicId).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        Long deleter =
                jdbc.queryForObject(
                        "SELECT deleted_by_member_id FROM post WHERE public_id = ?",
                        Long.class,
                        publicId);
        assertThat(deleter).isEqualTo(admin.getId());
        JsonNode item = adminItem("DELETED", publicId);
        assertThat(item.get("deleterRole").asText()).isEqualTo("ROLE_ADMIN");
        assertThat(item.get("isDeletedByAuthor").asBoolean()).isFalse();
        assertThat(item.has("deletedByMemberId")).isFalse();
        assertThat(item.has("authorMemberId")).isFalse();

        // 이미 지운 글은 다시 지우거나 블라인드할 수 없다.
        mockMvc.perform(delete(POSTS + "/" + publicId).session(adminSession).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(blind(publicId, true).session(adminSession).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공개 설정 API는 비로그인도 읽고, 비회원 라이브톡 여부만 내보낸다")
    void publicConfigsExposeOnlyChatFlag() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/api/v1/public/configs"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.anonymousChatEnabled").isBoolean())
                        .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("라이브톡 메시지 차단은 기간을 보내지 않으면 7일이다")
    void banByMessageDefaultsToSevenDays() throws Exception {
        String messageId = chatMessageFrom("198.51.100.71");

        mockMvc.perform(banByMessage(messageId, null).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        assertBannedForDays("198.51.100.71", 7);
    }

    @Test
    @DisplayName("라이브톡 메시지 차단은 고른 기간(30일)만큼 막는다")
    void banByMessageUsesChosenDays() throws Exception {
        String messageId = chatMessageFrom("198.51.100.72");

        mockMvc.perform(banByMessage(messageId, "{\"days\":30}").session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        assertBannedForDays("198.51.100.72", 30);
    }

    @Test
    @DisplayName("라이브톡 메시지 차단 기간이 1, 7, 30일이 아니면 400이고 차단하지 않는다")
    void banByMessageRejectsOtherDays() throws Exception {
        String messageId = chatMessageFrom("198.51.100.73");

        for (String body : List.of("{\"days\":5}", "{\"days\":0}", "{\"days\":-1}")) {
            mockMvc.perform(banByMessage(messageId, body).session(adminSession).with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        assertThat(banCount("198.51.100.73")).isZero();
    }

    @Test
    @DisplayName("일반 회원은 라이브톡 메시지 차단을 할 수 없다(403)")
    void banByMessageIsClosedToNonAdmins() throws Exception {
        String messageId = chatMessageFrom("198.51.100.74");

        mockMvc.perform(banByMessage(messageId, "{\"days\":1}").session(userSession).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(banCount("198.51.100.74")).isZero();
    }

    @Test
    @DisplayName("가린 댓글은 관리자에게만 blinded가 내려가고, 가린 글의 댓글도 관리자는 읽는다")
    void blindedFlagIsAdminOnly() throws Exception {
        Post post = savePost();
        Comment comment =
                comments.save(
                        Comment.create(post, null, null, "가릴 댓글", "192.0.2.5", true, "encoded"));

        mockMvc.perform(
                        patch("/api/v1/admin/comments/" + comment.getId() + "/blind")
                                .session(adminSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"blinded\":true}"))
                .andExpect(status().isNoContent());

        String url = "/api/v1/posts/" + post.getPublicId() + "/comments";
        mockMvc.perform(get(url).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].blinded").value(true));
        mockMvc.perform(get(url).session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].blinded").doesNotExist())
                .andExpect(jsonPath("$.comments[0].moderationHeld").doesNotExist());
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].blinded").doesNotExist());

        mockMvc.perform(blind(post.getPublicId(), true).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url).session(adminSession)).andExpect(status().isOk());
        mockMvc.perform(get(url).session(userSession)).andExpect(status().isNotFound());
    }

    private String chatMessageFrom(String ip) {
        bannedIps.add(ip);
        String messageId = UUID.randomUUID().toString();
        chatHistory.append(
                new ChatMessageResponse(
                        messageId,
                        new SenderDto("anon-" + messageId, "손님#0001"),
                        null,
                        "테스트 메시지",
                        LocalDateTime.now().toString()),
                ip);
        return messageId;
    }

    private MockHttpServletRequestBuilder banByMessage(String messageId, String body) {
        MockHttpServletRequestBuilder request =
                post("/api/v1/admin/chat/messages/" + messageId + "/ban-ip");
        return body == null
                ? request
                : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int banCount(String ip) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM chat_banned_ip WHERE banned_ip = ?",
                        Integer.class,
                        ip);
        return count == null ? 0 : count;
    }

    private void assertBannedForDays(String ip, int days) {
        LocalDateTime until =
                jdbc.queryForObject(
                        "SELECT banned_until FROM chat_banned_ip WHERE banned_ip = ?",
                        LocalDateTime.class,
                        ip);
        LocalDateTime expected = LocalDateTime.now().plusDays(days);
        assertThat(until).isBetween(expected.minusMinutes(5), expected.plusMinutes(1));
        assertThat(chatIpBanService.isBanned(ip)).isTrue();
    }

    private MockHttpServletRequestBuilder blind(String publicId, boolean blinded) {
        return patch(POSTS + "/" + publicId + "/blind")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blinded\":" + blinded + "}");
    }

    private List<String> adminList(String filter) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : adminContent(filter)) {
            ids.add(item.get("publicId").asText());
        }
        return ids;
    }

    private JsonNode adminItem(String filter, String publicId) throws Exception {
        for (JsonNode item : adminContent(filter)) {
            if (publicId.equals(item.get("publicId").asText())) {
                return item;
            }
        }
        throw new AssertionError("관리자 목록에 없음: " + publicId);
    }

    private JsonNode adminContent(String filter) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get(POSTS)
                                        .param("filter", filter)
                                        .param("size", "50")
                                        .session(adminSession))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
    }

    private String statusOf(String publicId) {
        return jdbc.queryForObject(
                "SELECT status FROM post WHERE public_id = ?", String.class, publicId);
    }

    private Post savePost() {
        PostCategory category =
                categories
                        .findByCode("FREE")
                        .orElseGet(
                                () ->
                                        categories.save(
                                                PostCategory.builder()
                                                        .code("FREE")
                                                        .name("자유")
                                                        .build()));
        return posts.save(
                Post.builder()
                        .category(category)
                        .title("관리자 운영 테스트")
                        .content("본문")
                        .writerIp("192.0.2.1")
                        .isAnonymous(true)
                        .build());
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
