package com.ikae.snowthing.domain.contentfilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.admin.service.ChatIpBanService;
import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.domain.contentfilter.service.BannedWordService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 금칙어 조용한 보류와 IP 대역 차단 통합 테스트 (IKA-96). 실제 보안 설정, 로그인 세션, 요청 IP로 작성자와 다른 사람이 보는 결과를 비교한다.
 *
 * <p>커밋 뒤 캐시를 다시 읽는 흐름을 보려고 테스트 트랜잭션으로 감싸지 않는다. 만든 행은 tearDown에서 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpamFilterIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String POSTS = "/api/v1/posts";
    private static final String WRITER_IP = "2001:db8:aa:1::10";
    private static final String OTHER_IP = "198.51.100.77";
    private static final String BANNED = "테스트광고어";

    /** 2026-11-24 운영에 올라온 광고 글. 등록된 금칙어는 하나도 없고 연락처만 있다. */
    private static final String CASINO_AD =
            "카카오톡: TAM999\n텔레그램: @Rtam999\n세부 누스타 카지노, 호텔 무료 객실 혜택도 함께 알아보세요!\n"
                    + "로얄탐에서 상담해 드립니다.\n#세부누스타 #누스타카지노 #세부카지노 #로얄탐";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberRepository members;
    @Autowired private PostCategoryRepository categories;
    @Autowired private BannedWordService bannedWordService;
    @Autowired private ChatIpBanService ipBanService;
    @Autowired private ChatService chatService;
    @Autowired private SearchSourceRepository searchSources;

    private final List<Long> memberIds = new ArrayList<>();
    private String marker;
    private Member admin;
    private Member writer;
    private MockHttpSession adminSession;
    private MockHttpSession writerSession;
    private MockHttpSession otherSession;

    @BeforeEach
    void setUp() throws Exception {
        category("ANONYMOUS", "익명게시판");
        category("FREE", "자유게시판");
        marker = "spam-" + UUID.randomUUID().toString().substring(0, 8);
        admin = member("adm", Role.ROLE_ADMIN);
        writer = member("wrt", Role.ROLE_USER);
        adminSession = login(admin);
        writerSession = login(writer);
        otherSession = login(member("oth", Role.ROLE_USER));
        addWord(BANNED).andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        List<Long> postIds =
                jdbc.queryForList(
                        "SELECT post_id FROM post WHERE title LIKE ?", Long.class, marker + "%");
        for (Long postId : postIds) {
            jdbc.update(
                    "DELETE FROM search_outbox WHERE source_type = 'COMMENT' AND source_id IN"
                            + " (SELECT comment_id FROM comment WHERE post_id = ?)",
                    postId);
            jdbc.update(
                    "DELETE FROM search_outbox WHERE source_type = 'POST' AND source_id = ?",
                    postId);
            jdbc.update("DELETE FROM content_filter_hold WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM comment WHERE post_id = ? AND parent_id IS NOT NULL", postId);
            jdbc.update("DELETE FROM comment WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
        }
        jdbc.update("DELETE FROM banned_word");
        jdbc.update("DELETE FROM chat_banned_ip WHERE reason LIKE ?", marker + "%");
        bannedWordService.reload();
        ipBanService.reload();
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM notification WHERE receiver_id = ?", memberId);
            jdbc.update("DELETE FROM admin_action_log WHERE actor_member_id = ?", memberId);
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("비회원 보류 글: 응답은 평소와 같고, 같은 IP 작성자와 관리자만 상세를 보며 다른 사람과 검색에는 없다")
    void anonymousHeldPostIsVisibleOnlyToWriter() throws Exception {
        JsonNode created =
                json(
                        mockMvc.perform(
                                        anonymousPost(marker + " 익명", "오늘 " + BANNED + " 확인")
                                                .with(ip(WRITER_IP)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("NORMAL"))
                                .andReturn());
        String publicId = created.get("publicId").asText();

        // 글 저장 직후 상세 화면으로 이동하는 작성자는 평소처럼 본다.
        mockMvc.perform(get(POSTS + "/" + publicId).with(ip(WRITER_IP)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));
        mockMvc.perform(get(POSTS + "/" + publicId + "/comments").with(ip(WRITER_IP)))
                .andExpect(status().isOk());
        // 다른 사람은 없는 글처럼 404, 목록에도 없다.
        mockMvc.perform(get(POSTS + "/" + publicId).with(ip(OTHER_IP)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(POSTS + "/" + publicId).session(otherSession).with(ip(OTHER_IP)))
                .andExpect(status().isNotFound());
        assertThat(listIds("ANONYMOUS", OTHER_IP, null)).doesNotContain(publicId);
        // 작성자 본인 IP로 보는 게시판 첫 쪽에는 끼워 넣는다.
        assertThat(listIds("ANONYMOUS", WRITER_IP, null)).contains(publicId);
        // 키워드 검색과 근거 검색(AI 검색 원문 조회)에는 작성자에게도 나오지 않는다.
        mockMvc.perform(
                        get(POSTS)
                                .param("keyword", marker)
                                .param("categoryCode", "ANONYMOUS")
                                .with(ip(WRITER_IP)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        assertThat(searchSources.findVisiblePostByPublicId(publicId)).isEmpty();
        // 관리자는 상세와 검토 목록에서 본다.
        mockMvc.perform(get(POSTS + "/" + publicId).session(adminSession).with(ip(OTHER_IP)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/filter-holds").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].postPublicId").value(publicId))
                .andExpect(jsonPath("$.items[0].targetType").value("POST"))
                .andExpect(jsonPath("$.items[0].matchedWord").value(BANNED))
                .andExpect(jsonPath("$.items[0].writerIp").value(WRITER_IP));
    }

    @Test
    @DisplayName("연락처: 금칙어가 없어도 비회원 광고 글은 보류하고 걸린 이유를 연락처로 남긴다 (IKA-122)")
    void anonymousContactPostIsHeld() throws Exception {
        String publicId =
                json(mockMvc.perform(
                                        anonymousPost(marker + " 세부 누스타", CASINO_AD)
                                                .with(ip(WRITER_IP)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("NORMAL"))
                                .andReturn())
                        .get("publicId")
                        .asText();

        mockMvc.perform(get(POSTS + "/" + publicId).with(ip(OTHER_IP)))
                .andExpect(status().isNotFound());
        assertThat(listIds("ANONYMOUS", OTHER_IP, null)).doesNotContain(publicId);
        mockMvc.perform(get("/api/v1/admin/filter-holds").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].postPublicId").value(publicId))
                .andExpect(jsonPath("$.items[0].matchedWord").value("연락처: 메신저 ID"));
    }

    @Test
    @DisplayName("연락처: 새 회원 글과 비회원 댓글은 보류하고, 가입 하루가 지난 회원과 관리자는 그대로다 (IKA-122)")
    void contactCheckAppliesOnlyToUnprovenWriters() throws Exception {
        String newMemberPost =
                createdStatusAndId(
                        memberPost(marker + " 새", "연락 010-1234-5678").session(writerSession));
        assertThat(postStatus(newMemberPost)).isEqualTo("FILTER_HOLD");

        jdbc.update(
                "UPDATE member SET created_at = DATE_SUB(NOW(6), INTERVAL 2 DAY) WHERE member_id = ?",
                writer.getId());
        String oldMemberPost =
                createdStatusAndId(
                        memberPost(marker + " 카풀", "카풀 연락 010-1234-5678").session(writerSession));
        assertThat(postStatus(oldMemberPost)).isEqualTo("NORMAL");

        String adminPost =
                createdStatusAndId(
                        memberPost(marker + " 공지", "문의 카카오톡: snowadmin").session(adminSession));
        assertThat(postStatus(adminPost)).isEqualTo("NORMAL");

        String plainAnonymous =
                createdStatusAndId(
                        anonymousPost(marker + " 후기", "공지 https://www.konjiamresort.co.kr 참고")
                                .with(ip(WRITER_IP)));
        assertThat(postStatus(plainAnonymous)).isEqualTo("NORMAL");

        mockMvc.perform(
                        post(POSTS + "/" + oldMemberPost + "/comments")
                                .with(csrf())
                                .with(ip(OTHER_IP))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "content",
                                                        "텔레그램 @Rtam999 상담",
                                                        "isAnonymous",
                                                        true,
                                                        "anonymousPassword",
                                                        "pass1234"))))
                .andExpect(status().isCreated());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT c.filter_state FROM comment c JOIN post p ON p.post_id = c.post_id"
                                        + " WHERE p.public_id = ?",
                                String.class,
                                oldMemberPost))
                .isEqualTo("HELD");
    }

    @Test
    @DisplayName("비회원 작성자 확인은 보류 뒤 1시간까지만이다")
    void anonymousWriterWindowExpires() throws Exception {
        String publicId = createAnonymousHeld();
        jdbc.update(
                "UPDATE content_filter_hold SET held_at = DATE_SUB(held_at, INTERVAL 2 HOUR)"
                        + " WHERE post_id = (SELECT post_id FROM post WHERE public_id = ?)",
                publicId);

        mockMvc.perform(get(POSTS + "/" + publicId).with(ip(WRITER_IP)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("회원 보류 글은 작성자 세션에만 보이고, 깨끗하게 고치면 모두에게 공개된다")
    void memberHeldPostAndCleanEdit() throws Exception {
        String publicId =
                json(mockMvc.perform(
                                        memberPost(marker + " 회원", "같이 " + BANNED)
                                                .session(writerSession)
                                                .with(ip(OTHER_IP)))
                                .andExpect(status().isCreated())
                                .andReturn())
                        .get("publicId")
                        .asText();

        mockMvc.perform(get(POSTS + "/" + publicId).session(writerSession))
                .andExpect(status().isOk());
        mockMvc.perform(get(POSTS + "/" + publicId).session(otherSession).with(ip(OTHER_IP)))
                .andExpect(status().isNotFound());
        assertThat(listIds("FREE", "203.0.113.200", writerSession)).contains(publicId);

        mockMvc.perform(
                        put(POSTS + "/" + publicId)
                                .session(writerSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "categoryCode",
                                                        "FREE",
                                                        "title",
                                                        marker + " 회원",
                                                        "content",
                                                        "깨끗한 본문"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(POSTS + "/" + publicId).session(otherSession))
                .andExpect(status().isOk());
        assertThat(holdCount()).isZero();
    }

    @Test
    @DisplayName("보류 댓글은 작성자에게만 보이고 댓글 수와 알림에 들어가지 않으며, 관리자가 공개하면 모두에게 보인다")
    void heldCommentAndRelease() throws Exception {
        String publicId =
                json(mockMvc.perform(memberPost(marker + " 원글", "평범한 글").session(otherSession))
                                .andExpect(status().isCreated())
                                .andReturn())
                        .get("publicId")
                        .asText();

        mockMvc.perform(
                        post(POSTS + "/" + publicId + "/comments")
                                .session(writerSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"content\":\"여기 "
                                                + BANNED
                                                + "\",\"isAnonymous\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDeleted").value(false))
                .andExpect(jsonPath("$.content").value("여기 " + BANNED));

        mockMvc.perform(get(POSTS + "/" + publicId + "/comments").session(writerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(1))
                .andExpect(jsonPath("$.comments[0].content").value("여기 " + BANNED));
        mockMvc.perform(get(POSTS + "/" + publicId + "/comments").session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(0))
                .andExpect(jsonPath("$.totalCommentCount").value(0));
        assertThat(notificationCount()).isZero();

        Long holdId =
                jdbc.queryForObject(
                        "SELECT content_filter_hold_id FROM content_filter_hold WHERE"
                                + " target_type = 'COMMENT' AND post_id = (SELECT post_id FROM post"
                                + " WHERE public_id = ?)",
                        Long.class,
                        publicId);
        mockMvc.perform(
                        post("/api/v1/admin/filter-holds/" + holdId + "/release")
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(POSTS + "/" + publicId + "/comments").session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments.length()").value(1))
                .andExpect(jsonPath("$.totalCommentCount").value(1));
        assertThat(actionCount("FILTER_HOLD_RELEASE")).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자가 보류 글을 삭제하면 작성자에게도 사라지고 조치 기록이 남는다")
    void adminDeletesHeldPost() throws Exception {
        String publicId = createAnonymousHeld();
        Long holdId =
                jdbc.queryForObject(
                        "SELECT MAX(content_filter_hold_id) FROM content_filter_hold", Long.class);

        mockMvc.perform(
                        delete("/api/v1/admin/filter-holds/" + holdId)
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(POSTS + "/" + publicId).with(ip(WRITER_IP)))
                .andExpect(status().isNotFound());
        assertThat(holdCount()).isZero();
        assertThat(actionCount("FILTER_HOLD_DELETE")).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자가 금칙어를 바꾸면 재시작 없이 다음 글부터 반영된다. 관리자 글은 검사하지 않는다")
    void bannedWordCacheRefreshAndAdminExempt() throws Exception {
        String word = "새광고말";
        mockMvc.perform(anonymousPost(marker + " 전", "본문 " + word).with(ip(OTHER_IP)))
                .andExpect(status().isCreated());
        assertThat(holdCount()).isZero();

        JsonNode added = json(addWord("새 광고-말").andExpect(status().isOk()).andReturn());
        mockMvc.perform(anonymousPost(marker + " 후", "본문 " + word).with(ip(OTHER_IP)))
                .andExpect(status().isCreated());
        assertThat(holdCount()).isEqualTo(1);
        addWord("새광고말").andExpect(status().isConflict());

        mockMvc.perform(memberPost(marker + " 관리자", "공지 " + word).session(adminSession))
                .andExpect(status().isCreated());
        assertThat(holdCount()).isEqualTo(1);

        mockMvc.perform(
                        delete("/api/v1/admin/banned-words/" + added.get("id").asText())
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(anonymousPost(marker + " 삭제 뒤", "본문 " + word).with(ip(OTHER_IP)))
                .andExpect(status().isCreated());
        assertThat(holdCount()).isEqualTo(1);
        assertThat(actionCount("BANNED_WORD_CREATE")).isEqualTo(2);
        assertThat(actionCount("BANNED_WORD_DELETE")).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 API는 일반 회원 403, 비로그인 401")
    void adminApisAreAdminOnly() throws Exception {
        for (String path : List.of("/api/v1/admin/banned-words", "/api/v1/admin/filter-holds")) {
            mockMvc.perform(get(path).session(writerSession)).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(
                        post("/api/v1/admin/banned-words")
                                .session(writerSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"word\":\"막을말\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/api/v1/admin/filter-holds/1/release")
                                .session(writerSession)
                                .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/api/v1/admin/chat/banned-ips")
                                .session(writerSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bannedIp\":\"203.0.113.0/24\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IPv6 /64 차단은 글, 댓글, 라이브톡을 일반 오류로 막고 다른 대역은 막지 않는다")
    void rangeBanBlocksWrites() throws Exception {
        String target =
                json(mockMvc.perform(memberPost(marker + " 대상", "평범").session(otherSession))
                                .andExpect(status().isCreated())
                                .andReturn())
                        .get("publicId")
                        .asText();
        mockMvc.perform(
                        post("/api/v1/admin/chat/banned-ips")
                                .session(adminSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"bannedIp\":\"2001:db8:bad:1::/64\",\"reason\":\""
                                                + marker
                                                + " 광고\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannedIp").value("2001:db8:bad:1::/64"));

        String bannedHost = "2001:db8:bad:1:9999:8888:7777:6666";
        mockMvc.perform(anonymousPost(marker + " 차단", "본문").with(ip(bannedHost)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_007"));
        mockMvc.perform(
                        post(POSTS + "/" + target + "/comments")
                                .with(csrf())
                                .with(ip(bannedHost))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"content\":\"댓글\",\"isAnonymous\":true,"
                                                + "\"anonymousPassword\":\"pass1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_007"));
        assertThatThrownBy(
                        () ->
                                chatService.processMessage(
                                        new ChatMessageRequest(null, "안녕"), null, bannedHost))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_IP_BANNED);

        mockMvc.perform(anonymousPost(marker + " 옆 대역", "본문").with(ip("2001:db8:bad:2::1")))
                .andExpect(status().isCreated());
        mockMvc.perform(
                        post("/api/v1/admin/chat/banned-ips")
                                .session(adminSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"bannedIp\":\"2001:db8::/16\",\"reason\":\""
                                                + marker
                                                + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_008"));
    }

    @Test
    @DisplayName("보류 항목에서 작성자 IPv6 /64 대역을 바로 막는다")
    void banFromHoldItem() throws Exception {
        createAnonymousHeld();
        Long holdId =
                jdbc.queryForObject(
                        "SELECT MAX(content_filter_hold_id) FROM content_filter_hold", Long.class);
        jdbc.update(
                "UPDATE content_filter_hold SET writer_ip = ? WHERE content_filter_hold_id = ?",
                WRITER_IP,
                holdId);

        mockMvc.perform(
                        post("/api/v1/admin/filter-holds/" + holdId + "/ban-ip")
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannedIp").value("2001:db8:aa:1::/64"));
        jdbc.update(
                "UPDATE chat_banned_ip SET reason = ? WHERE banned_ip = ?",
                marker + " 보류",
                "2001:db8:aa:1::/64");

        assertThat(ipBanService.isBanned("2001:db8:aa:1::abcd")).isTrue();
    }

    private String createAnonymousHeld() throws Exception {
        return json(mockMvc.perform(
                                anonymousPost(marker + " 보류", "광고 " + BANNED).with(ip(WRITER_IP)))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("publicId")
                .asText();
    }

    private List<String> listIds(String category, String viewerIp, MockHttpSession session)
            throws Exception {
        MockHttpServletRequestBuilder request =
                get(POSTS).param("categoryCode", category).param("size", "50").with(ip(viewerIp));
        if (session != null) request.session(session);
        JsonNode content =
                json(mockMvc.perform(request).andExpect(status().isOk()).andReturn())
                        .get("content");
        List<String> ids = new ArrayList<>();
        content.forEach(node -> ids.add(node.get("publicId").asText()));
        return ids;
    }

    private MockHttpServletRequestBuilder anonymousPost(String title, String content)
            throws Exception {
        return post(POSTS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        objectMapper.writeValueAsString(
                                Map.of(
                                        "categoryCode",
                                        "ANONYMOUS",
                                        "title",
                                        title,
                                        "content",
                                        content,
                                        "isAnonymous",
                                        true,
                                        "anonymousPassword",
                                        "pass1234")));
    }

    private MockHttpServletRequestBuilder memberPost(String title, String content)
            throws Exception {
        return post(POSTS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        objectMapper.writeValueAsString(
                                Map.of(
                                        "categoryCode",
                                        "FREE",
                                        "title",
                                        title,
                                        "content",
                                        content,
                                        "isAnonymous",
                                        false)));
    }

    private org.springframework.test.web.servlet.ResultActions addWord(String word)
            throws Exception {
        return mockMvc.perform(
                post("/api/v1/admin/banned-words")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("word", word))));
    }

    private static RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createdStatusAndId(MockHttpServletRequestBuilder request) throws Exception {
        return json(mockMvc.perform(request).andExpect(status().isCreated()).andReturn())
                .get("publicId")
                .asText();
    }

    private String postStatus(String publicId) {
        return jdbc.queryForObject(
                "SELECT status FROM post WHERE public_id = ?", String.class, publicId);
    }

    private int holdCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_filter_hold h JOIN post p ON p.post_id = h.post_id"
                        + " WHERE p.title LIKE ?",
                Integer.class,
                marker + "%");
    }

    private int actionCount(String action) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_action_log WHERE actor_member_id = ? AND action = ?",
                Integer.class,
                admin.getId(),
                action);
    }

    private int notificationCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification n JOIN member m ON m.member_id = n.receiver_id"
                        + " WHERE m.member_id IN ("
                        + String.join(",", memberIds.stream().map(String::valueOf).toList())
                        + ")",
                Integer.class);
    }

    private void category(String code, String name) {
        categories
                .findByCode(code)
                .orElseGet(
                        () ->
                                categories.save(
                                        PostCategory.builder().name(name).code(code).build()));
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
