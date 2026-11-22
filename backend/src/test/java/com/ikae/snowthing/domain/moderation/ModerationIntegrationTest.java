package com.ikae.snowthing.domain.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.moderation.entity.ModerationStatus;
import com.ikae.snowthing.domain.moderation.entity.ModerationVerdict;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository;
import com.ikae.snowthing.domain.moderation.service.ModerationEnqueuer;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;

/**
 * AI 자동 검열 1차 통합 테스트 (IKA-80). 2단계(Moderation API)는 꺼진 기본 설정이라 1단계 규칙만 돈다.
 *
 * <p>대기열 적재(같은 트랜잭션), 규칙 판정, 자동 숨김, 묶음 처리와 재시작 뒤 이어 처리, 관리자 검토 API를 실제 MySQL로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModerationIntegrationTest extends ModerationTestSupport {

    private static final String ADMIN_API = "/api/v1/admin/moderations";

    /** 텔레그램 연락 + 새 계정의 외부 링크 3개: 두 묶음이 함께 걸리는 "확실한 스팸". */
    private static final String CERTAIN_SPAM =
            "가입 문의 t.me/dealmaker https://a.example.com/1 https://b.example.com/2"
                    + " https://c.example.com/3";

    @Autowired private ModerationEnqueuer enqueuer;
    @Autowired private ContentModerationRepository moderationRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCategoryRepository postCategoryRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Member writer;
    private MockHttpSession writerSession;
    private MockHttpSession otherSession;
    private MockHttpSession adminSession;
    private Member admin;

    @BeforeEach
    void setUp() throws Exception {
        category("FREE", "자유게시판");
        category("INQUIRY", "1:1 문의");
        category("MARKET", "중고장터");
        // 앞선 테스트가 남긴 줄을 먼저 비워 이 테스트의 묶음 크기를 예측할 수 있게 한다.
        drain();
        writer = member("wrt", Role.ROLE_USER);
        admin = member("adm", Role.ROLE_ADMIN);
        writerSession = login(writer);
        otherSession = login(member("oth", Role.ROLE_USER));
        adminSession = login(admin);
    }

    @Test
    @DisplayName("글과 댓글을 쓰고 고칠 때 대기 줄이 생기고, 본문 없이 해시와 작성자 정보만 남는다. 관리자 글과 1:1 문의는 넣지 않는다")
    void enqueuesOnWriteAndEdit() throws Exception {
        long postId = writePost(writerSession, "첫 글", "오늘 용평 설질 좋네요");
        assertThat(rowCount("POST", postId)).isEqualTo(1);
        Map<String, Object> row = latest("POST", postId);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("member_id")).isEqualTo(writer.getId());
        assertThat(row.get("writer_ip")).isEqualTo(WRITER_IP);
        assertThat((String) row.get("content_hash")).hasSize(64);
        assertThat(row.values()).noneMatch(value -> String.valueOf(value).contains("설질"));

        editPost(writerSession, postId, "첫 글", "고친 본문입니다");
        assertThat(rowCount("POST", postId)).isEqualTo(2);

        long commentId = writeComment(otherSession, postId, "저도 다녀왔어요");
        assertThat(rowCount("COMMENT", commentId)).isEqualTo(1);

        long adminPost = writePost(adminSession, "공지 같은 글", "관리자가 쓴 글 t.me/abc");
        assertThat(rowCount("POST", adminPost)).isZero();

        mockMvc.perform(
                        post(POSTS)
                                .session(writerSession)
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "categoryCode",
                                                        "INQUIRY",
                                                        "title",
                                                        marker + " 문의",
                                                        "content",
                                                        "결제 문의 t.me/abc",
                                                        "isAnonymous",
                                                        false))))
                .andExpect(status().isCreated());
        Long inquiryId =
                jdbc.queryForObject(
                        "SELECT post_id FROM post WHERE title = ?", Long.class, marker + " 문의");
        assertThat(rowCount("POST", inquiryId)).isZero();
    }

    @Test
    @DisplayName("원문 저장이 롤백되면 대기 줄도 남지 않고, 트랜잭션 밖에서는 넣을 수 없다")
    void enqueueSharesTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long postId =
                tx.execute(
                        status -> {
                            Post post =
                                    postRepository.save(
                                            Post.builder()
                                                    .member(writer)
                                                    .category(
                                                            postCategoryRepository
                                                                    .findByCode("FREE")
                                                                    .orElseThrow())
                                                    .title(marker + " 롤백")
                                                    .content("롤백될 글")
                                                    .writerIp(WRITER_IP)
                                                    .isAnonymous(false)
                                                    .hasImage(false)
                                                    .build());
                            enqueuer.postWritten(post);
                            assertThat(rowCount("POST", post.getId())).isEqualTo(1);
                            status.setRollbackOnly();
                            return post.getId();
                        });

        assertThat(rowCount("POST", postId)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM post WHERE post_id = ?",
                                Integer.class,
                                postId))
                .isZero();
        assertThatThrownBy(() -> enqueuer.postWritten(null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("정상 글은 통과, 외부 연락 하나만 걸리면 검토 대기, 정치 글은 숨기지 않고 검토 대기")
    void ruleVerdicts() throws Exception {
        long normal = writePost(writerSession, "정상", "곤지암 리프트권 양도합니다. 카톡으로 연락 주세요");
        long contact = writePost(writerSession, "연락", "문의는 t.me/snowdeal 로 주세요");
        long political = writePost(writerSession, "정치", "이번 대선 토론 보셨어요? 의견 궁금하네요");

        drain();

        assertThat(latest("POST", normal))
                .containsEntry("status", "DONE")
                .containsEntry("verdict", "PASS")
                .containsEntry("stage", "RULE");
        assertThat(latest("POST", contact)).containsEntry("verdict", "REVIEW");
        assertThat((String) latest("POST", contact).get("reasons")).contains("CONTACT_TELEGRAM");
        assertThat(latest("POST", political)).containsEntry("verdict", "REVIEW");
        assertThat((String) latest("POST", political).get("reasons")).contains("POLITICAL");
        assertThat(postStatus(normal)).isEqualTo("NORMAL");
        assertThat(postStatus(contact)).isEqualTo("NORMAL");
        assertThat(postStatus(political)).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("확실한 스팸은 숨긴다. AI 회원 글도 사람 글과 똑같이 판정하고 숨긴다")
    void certainSpamHiddenSameForAiMember() throws Exception {
        Member aiMember = member("ai", Role.ROLE_USER, "@snowthing.invalid");
        MockHttpSession aiSession = login(aiMember);

        long human = writePost(writerSession, "사람 광고", CERTAIN_SPAM);
        long ai = writePost(aiSession, "AI 광고", CERTAIN_SPAM);
        drain();

        for (long postId : List.of(human, ai)) {
            Map<String, Object> row = latest("POST", postId);
            assertThat(row).containsEntry("verdict", "HIDE").containsEntry("stage", "RULE");
            assertThat((String) row.get("reasons"))
                    .contains("CONTACT_TELEGRAM")
                    .contains("NEW_ACCOUNT_LINKS");
            assertThat(postStatus(postId)).isEqualTo("BLOCKED");
            mockMvc.perform(get(POSTS + "/" + publicId(postId)).session(otherSession))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("같은 작성자가 같은 내용을 10분 안에 세 번째 올리면 반복으로 보고, 외부 연락과 겹치면 숨긴다")
    void duplicateRule() throws Exception {
        String repeated = "주말에 같이 타실 분 구해요 초보도 환영합니다";
        long first = writePost(writerSession, "반복1", repeated);
        long second = writePost(writerSession, "반복1", repeated);
        long third = writePost(writerSession, "반복1", repeated);
        String withContact = "주말 시즌권 싸게 드립니다 문의 t.me/cheapseason";
        long a = writePost(writerSession, "반복2", withContact);
        writePost(writerSession, "반복2", withContact);
        long c = writePost(writerSession, "반복2", withContact);
        drain();

        assertThat(latest("POST", first)).containsEntry("verdict", "PASS");
        assertThat(latest("POST", second)).containsEntry("verdict", "PASS");
        assertThat(latest("POST", third)).containsEntry("verdict", "REVIEW");
        assertThat((String) latest("POST", third).get("reasons")).contains("DUPLICATE");
        assertThat(latest("POST", a)).containsEntry("verdict", "REVIEW");
        assertThat(latest("POST", c)).containsEntry("verdict", "HIDE");
        assertThat(postStatus(c)).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("댓글 자동 숨김은 관리자 댓글 블라인드와 같은 상태(moderation_held)를 쓰고 다른 사람에게 내용이 보이지 않는다")
    void commentHidden() throws Exception {
        long postId = writePost(otherSession, "원글", "오늘 휘닉스 어때요");
        long commentId = writeComment(writerSession, postId, CERTAIN_SPAM);
        drain();

        assertThat(latest("COMMENT", commentId)).containsEntry("verdict", "HIDE");
        assertThat(
                        jdbc.queryForMap(
                                "SELECT is_deleted, moderation_held FROM comment WHERE comment_id ="
                                        + " ?",
                                commentId))
                .containsEntry("is_deleted", true)
                .containsEntry("moderation_held", true);
        mockMvc.perform(get(POSTS + "/" + publicId(postId) + "/comments").session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].content").value("임시조치된 댓글입니다."));
    }

    @Test
    @DisplayName("장터 글은 카톡 아이디를 보지 않고, 숨길 때는 장터 숨김(HIDDEN)을 쓴다")
    void marketContactAllowedAndHiddenStatus() {
        long allowed = marketPost("보드 팝니다", "카톡 아이디 snow1234 로 연락 주세요");
        long spam = marketPost("급처", CERTAIN_SPAM);
        drain();

        assertThat(latest("MARKET", allowed)).containsEntry("verdict", "PASS");
        assertThat(latest("MARKET", spam)).containsEntry("verdict", "HIDE");
        assertThat(postStatus(allowed)).isEqualTo("NORMAL");
        assertThat(postStatus(spam)).isEqualTo("HIDDEN");
    }

    @Test
    @DisplayName("처리 전에 고친 글은 새 줄만 판정하고, 지운 글은 판정하지 않는다")
    void skipsSupersededAndDeleted() throws Exception {
        long edited = writePost(writerSession, "고침", "처음 본문 t.me/abc");
        editPost(writerSession, edited, "고침", "깨끗하게 고친 본문입니다");
        long deleted = writePost(writerSession, "지움", CERTAIN_SPAM);
        mockMvc.perform(
                        delete(POSTS + "/" + publicId(deleted))
                                .session(writerSession)
                                .with(csrf())
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().is2xxSuccessful());
        drain();

        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT status, verdict FROM content_moderation WHERE target_type = 'POST'"
                                + " AND target_id = ? ORDER BY content_moderation_id",
                        edited);
        assertThat(rows.get(0)).containsEntry("status", "SKIPPED");
        assertThat(rows.get(1)).containsEntry("status", "DONE").containsEntry("verdict", "PASS");
        assertThat(latest("POST", deleted)).containsEntry("status", "SKIPPED");
    }

    @Test
    @DisplayName("한 번에 여러 줄을 묶어 처리한다")
    void processesBatch() throws Exception {
        long[] ids = new long[5];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = writePost(writerSession, "묶음 " + i, "묶음 처리 확인용 글입니다 " + i);
        }

        int claimed = processor.processBatch();

        assertThat(claimed).isGreaterThanOrEqualTo(ids.length);
        for (long id : ids) {
            assertThat(latest("POST", id)).containsEntry("status", "DONE");
        }
    }

    @Test
    @DisplayName("처리 중 서버가 죽어도 임대가 끝나면 다시 가져가 이어서 처리한다")
    void resumesAfterRestart() throws Exception {
        long postId = writePost(writerSession, "재시작", CERTAIN_SPAM);
        LocalDateTime now = LocalDateTime.now();
        // 가져가기만 하고 판정 전에 죽은 상황: 임대와 시도 횟수만 남는다.
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> moderationRepository.claim(now, now.plusMinutes(5), 50));
        assertThat(latest("POST", postId))
                .containsEntry("status", "PENDING")
                .containsEntry("attempt_count", 1);

        drain();
        assertThat(latest("POST", postId)).containsEntry("status", "PENDING");
        assertThat(postStatus(postId)).isEqualTo("NORMAL");

        jdbc.update(
                "UPDATE content_moderation SET lease_until = ? WHERE target_type = 'POST'"
                        + " AND target_id = ?",
                now.minusSeconds(1),
                postId);
        drain();

        assertThat(latest("POST", postId))
                .containsEntry("status", "DONE")
                .containsEntry("verdict", "HIDE")
                .containsEntry("attempt_count", 2);
        assertThat(postStatus(postId)).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("임대가 끝나 다른 처리기가 다시 가져간 줄은 이전 시도가 기록하거나 임대를 지우지 못한다")
    void staleAttemptCannotWrite() throws Exception {
        long postId = writePost(writerSession, "이전 시도", "임대 확인용 글입니다");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        LocalDateTime now = LocalDateTime.now();
        ContentModerationRepository.Row stale =
                tx.execute(status -> moderationRepository.claim(now, now.plusMinutes(5), 50))
                        .stream()
                        .filter(row -> row.targetId() == postId)
                        .findFirst()
                        .orElseThrow();
        jdbc.update(
                "UPDATE content_moderation SET lease_until = ? WHERE content_moderation_id = ?",
                now.minusSeconds(1),
                stale.id());
        ContentModerationRepository.Row current =
                tx.execute(status -> moderationRepository.claim(now, now.plusMinutes(5), 50))
                        .stream()
                        .filter(row -> row.id() == stale.id())
                        .findFirst()
                        .orElseThrow();
        assertThat(current.attempt()).isEqualTo(stale.attempt() + 1);

        Optional<ContentModerationRepository.Row> locked =
                tx.execute(status -> moderationRepository.lockPending(stale));
        assertThat(locked).isEmpty();
        assertThat(moderationRepository.scheduleRetry(stale, now.plusMinutes(1), now)).isZero();
        assertThat(
                        moderationRepository.complete(
                                stale, ModerationStatus.DONE, null, ModerationVerdict.PASS, null, now))
                .isZero();
        assertThat(latest("POST", postId))
                .containsEntry("status", "PENDING")
                .extracting(row -> row.get("lease_until"))
                .isNotNull();

        assertThat(
                        moderationRepository.complete(
                                current, ModerationStatus.DONE, null, ModerationVerdict.PASS, null, now))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 API는 일반 회원 403, 비로그인 401")
    void adminApisAreAdminOnly() throws Exception {
        mockMvc.perform(get(ADMIN_API).session(writerSession)).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_API)).andExpect(status().isUnauthorized());
        for (String action : List.of("pass", "keep")) {
            mockMvc.perform(post(ADMIN_API + "/1/" + action).session(writerSession).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(delete(ADMIN_API + "/1").session(writerSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("문제없음은 숨김을 풀고 같은 내용은 다시 숨기지 않는다")
    void passReleasesAndClearsHash() throws Exception {
        long postId = writePost(writerSession, "오판정", CERTAIN_SPAM);
        drain();
        long rowId = ((Number) latest("POST", postId).get("content_moderation_id")).longValue();
        assertThat(postStatus(postId)).isEqualTo("BLOCKED");

        JsonNode item = queueItem(rowId);
        assertThat(item.get("verdict").asText()).isEqualTo("HIDE");
        assertThat(item.get("targetType").asText()).isEqualTo("POST");
        assertThat(item.get("targetState").asText()).isEqualTo("HIDDEN");
        assertThat(item.get("postPublicId").asText()).isEqualTo(publicId(postId));
        assertThat(item.get("reasons").toString()).contains("텔레그램 연락", "새 계정 링크 여러 개");

        mockMvc.perform(post(ADMIN_API + "/" + rowId + "/pass").session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(postStatus(postId)).isEqualTo("NORMAL");
        assertThat(latest("POST", postId)).containsEntry("review_result", "PASS");
        assertThat(latest("POST", postId).get("reviewed_by")).isEqualTo(admin.getId());
        assertThat(actionCount("MODERATION_PASS")).isEqualTo(1);
        assertThat(actionCount("POST_UNBLIND")).isEqualTo(1);
        assertThat(queueItem(rowId)).isNull();
        mockMvc.perform(post(ADMIN_API + "/" + rowId + "/pass").session(adminSession).with(csrf()))
                .andExpect(status().isNotFound());

        long again = writePost(writerSession, "오판정", CERTAIN_SPAM);
        drain();
        assertThat(latest("POST", again)).containsEntry("verdict", "PASS");
        assertThat((String) latest("POST", again).get("reasons")).contains("CLEARED_BEFORE");
        assertThat(postStatus(again)).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("숨김 유지는 검토 대기 글을 숨기고, 삭제는 기존 관리자 삭제 흐름을 탄다")
    void keepAndDelete() throws Exception {
        long keep = writePost(writerSession, "유지", "문의는 t.me/keepme 로");
        long remove = writePost(writerSession, "삭제", "문의는 t.me/removeme 로");
        drain();
        long keepRow = ((Number) latest("POST", keep).get("content_moderation_id")).longValue();
        long removeRow = ((Number) latest("POST", remove).get("content_moderation_id")).longValue();

        mockMvc.perform(
                        post(ADMIN_API + "/" + keepRow + "/keep")
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ADMIN_API + "/" + removeRow).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(postStatus(keep)).isEqualTo("BLOCKED");
        assertThat(latest("POST", keep)).containsEntry("review_result", "KEEP");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_deleted FROM post WHERE post_id = ?",
                                Boolean.class,
                                remove))
                .isTrue();
        assertThat(latest("POST", remove)).containsEntry("review_result", "DELETE");
        assertThat(actionCount("MODERATION_KEEP")).isEqualTo(1);
        assertThat(actionCount("MODERATION_DELETE")).isEqualTo(1);
    }

    @Test
    @DisplayName("이전 검토 대기 판정을 처리해도 같은 대상의 더 새 판정은 닫지 않는다")
    void reviewClosesOnlyOlderRows() throws Exception {
        long postId = writePost(writerSession, "이전 판정", "문의는 t.me/olderrow 로");
        drain();
        long older = ((Number) latest("POST", postId).get("content_moderation_id")).longValue();
        // 판정이 엇갈려 이전 줄보다 새 HIDE 줄이 아직 검토되지 않고 남은 상황.
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                "INSERT INTO content_moderation (target_type, target_id, content_hash, status,"
                        + " verdict, attempt_count, next_attempt_at, created_at, updated_at,"
                        + " processed_at) VALUES ('POST', ?, 'x', 'DONE', 'HIDE', 1, ?, ?, ?, ?)",
                postId,
                now,
                now,
                now,
                now);
        long newer = ((Number) latest("POST", postId).get("content_moderation_id")).longValue();

        mockMvc.perform(post(ADMIN_API + "/" + older + "/pass").session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT review_result FROM content_moderation"
                                        + " WHERE content_moderation_id = ?",
                                String.class,
                                older))
                .isEqualTo("PASS");
        assertThat(latest("POST", postId).get("review_result")).isNull();
        assertThat(queueItem(newer)).isNotNull();
    }

    private long marketPost(String title, String content) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(
                status -> {
                    Post post =
                            postRepository.save(
                                    Post.builder()
                                            .member(writer)
                                            .category(
                                                    postCategoryRepository
                                                            .findByCode("MARKET")
                                                            .orElseThrow())
                                            .title(marker + " " + title)
                                            .content(content)
                                            .writerIp(WRITER_IP)
                                            .isAnonymous(false)
                                            .hasImage(false)
                                            .build());
                    enqueuer.marketWritten(post);
                    return post.getId();
                });
    }

    /** 검토 대기 목록 첫 쪽에서 이 줄을 찾는다. 없으면 null. */
    private JsonNode queueItem(long rowId) throws Exception {
        JsonNode items =
                objectMapper
                        .readTree(
                                mockMvc.perform(
                                                get(ADMIN_API)
                                                        .param("size", "50")
                                                        .session(adminSession))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .get("items");
        for (JsonNode item : items) {
            if (String.valueOf(rowId).equals(item.get("id").asText())) {
                return item;
            }
        }
        return null;
    }

    private int actionCount(String action) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_action_log WHERE actor_member_id = ? AND action = ?",
                Integer.class,
                admin.getId(),
                action);
    }
}
