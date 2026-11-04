package com.ikae.snowthing.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.search.dto.SearchReindexResponse;
import com.ikae.snowthing.testsupport.SearchDocumentFullTextIndex;

/**
 * 기동 시 빈 색인 채우기. 테스트 설정은 처리기 스케줄러를 끄므로(indexer.enabled=false) 기동 때는 실행되지 않고, 테스트에서 플래그를 바꿔 직접 부른다.
 * IKA-10 이전에 만들어진 글처럼 outbox 없이 게시글을 저장해 둔다.
 */
@SpringBootTest
class SearchIndexBootstrapIntegrationTest {

    @Autowired private SearchIndexBootstrap bootstrap;
    @Autowired private SearchOutboxProcessor processor;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MemberRepository members;
    @Autowired private PostCategoryRepository categories;
    @Autowired private PostRepository posts;

    private final List<Long> postIds = new ArrayList<>();
    private Long memberId;
    private Long categoryId;
    private long postId;

    @BeforeEach
    void setUp() {
        SearchDocumentFullTextIndex.ensure(jdbc);
        clearSearchTables();
        ReflectionTestUtils.setField(bootstrap, "indexerEnabled", true);
        ReflectionTestUtils.setField(bootstrap, "bootstrapOnEmpty", true);
        postId =
                transactions.execute(
                        status -> {
                            String unique = UUID.randomUUID().toString();
                            Member member =
                                    members.save(
                                            Member.builder()
                                                    .publicId(UUID.randomUUID().toString())
                                                    .email(unique + "@test.invalid")
                                                    .nickname(("b" + unique).substring(0, 12))
                                                    .role(Role.ROLE_USER)
                                                    .build());
                            memberId = member.getId();
                            PostCategory category =
                                    categories.save(
                                            PostCategory.builder()
                                                    .code(
                                                            "BOOT_"
                                                                    + unique.replace("-", "")
                                                                            .substring(0, 10)
                                                                            .toUpperCase(
                                                                                    Locale.ROOT))
                                                    .name("기동 색인 테스트")
                                                    .build());
                            categoryId = category.getId();
                            Post post =
                                    posts.save(
                                            Post.builder()
                                                    .member(member)
                                                    .category(category)
                                                    .title("배포 전부터 있던 글")
                                                    .content("바인딩 각도 본문")
                                                    .writerIp("127.0.0.1")
                                                    .status(PostStatus.NORMAL)
                                                    .build());
                            postIds.add(post.getId());
                            return post.getId();
                        });
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(bootstrap, "indexerEnabled", false);
        clearSearchTables();
        transactions.executeWithoutResult(
                status -> {
                    for (Long id : postIds) {
                        jdbc.update("DELETE FROM post WHERE post_id = ?", id);
                    }
                    jdbc.update("DELETE FROM post_category WHERE category_id = ?", categoryId);
                    jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
                });
    }

    @Test
    @DisplayName("색인 상태가 비어 있고 공개 글이 있으면 재인덱싱을 끝까지 넣고, 처리기가 문서를 만든다")
    void emptyIndexTriggersReindex() {
        SearchReindexResponse response = bootstrap.bootstrapIfEmpty();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(postEvents()).isEqualTo(1);

        for (int i = 0; i < 20 && processor.processBatch() > 0; i++) {
            // 남은 이벤트가 없을 때까지
        }
        assertThat(
                        jdbc.queryForList(
                                "SELECT evidence_id FROM search_document WHERE post_id = ?",
                                String.class,
                                postId))
                .containsExactlyInAnyOrder("P:" + postId + ":0", "P:" + postId + ":1");

        // 한 번 색인된 뒤에는 다시 기동해도 실행하지 않는다.
        assertThat(bootstrap.bootstrapIfEmpty()).isNull();
        assertThat(postEvents()).isEqualTo(1);
    }

    @Test
    @DisplayName("색인 상태 행이 하나라도 있으면 실행하지 않는다")
    void nonEmptyIndexDoesNotTrigger() {
        transactions.executeWithoutResult(
                status ->
                        jdbc.update(
                                "INSERT INTO search_index_state (source_type, source_id,"
                                        + " applied_version, tombstoned, document_count, updated_at)"
                                        + " VALUES ('POST', 999999999, 0, false, 1, ?)",
                                LocalDateTime.now()));

        assertThat(bootstrap.bootstrapIfEmpty()).isNull();
        assertThat(postEvents()).isZero();
        assertThat(checkpointStatus()).isEmpty();
    }

    @Test
    @DisplayName("플래그가 꺼져 있거나 처리기가 꺼져 있으면 실행하지 않는다")
    void flagsOffDoNotTrigger() {
        ReflectionTestUtils.setField(bootstrap, "bootstrapOnEmpty", false);
        assertThat(bootstrap.bootstrapIfEmpty()).isNull();

        ReflectionTestUtils.setField(bootstrap, "bootstrapOnEmpty", true);
        ReflectionTestUtils.setField(bootstrap, "indexerEnabled", false);
        assertThat(bootstrap.bootstrapIfEmpty()).isNull();

        assertThat(postEvents()).isZero();
        assertThat(checkpointStatus()).isEmpty();
    }

    @Test
    @DisplayName("다른 인스턴스가 이미 끝낸 재인덱싱(DONE)은 다시 하지 않고, 멈춘 재인덱싱(RUNNING)은 이어서 한다")
    void respectsExistingCheckpoint() {
        insertCheckpoint("DONE");
        assertThat(bootstrap.bootstrapIfEmpty()).isNull();
        assertThat(postEvents()).isZero();

        jdbc.update("DELETE FROM search_reindex_checkpoint");
        insertCheckpoint("RUNNING");
        SearchReindexResponse resumed = bootstrap.bootstrapIfEmpty();
        assertThat(resumed).isNotNull();
        assertThat(resumed.resumed()).isTrue();
        assertThat(resumed.status()).isEqualTo("DONE");
        assertThat(postEvents()).isEqualTo(1);
    }

    private void insertCheckpoint(String status) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                "INSERT INTO search_reindex_checkpoint (job_name, status, last_source_id,"
                        + " enqueued_count, started_at, updated_at, finished_at)"
                        + " VALUES ('FULL', ?, 0, 0, ?, ?, ?)",
                status,
                now,
                now,
                "DONE".equals(status) ? now : null);
    }

    private int postEvents() {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM search_outbox WHERE source_type = 'POST'"
                                + " AND source_id = ?",
                        Integer.class,
                        postId);
        return count == null ? 0 : count;
    }

    private List<String> checkpointStatus() {
        return jdbc.queryForList("SELECT status FROM search_reindex_checkpoint", String.class);
    }

    private void clearSearchTables() {
        jdbc.update("DELETE FROM search_outbox");
        jdbc.update("DELETE FROM search_index_state");
        jdbc.update("DELETE FROM search_document");
        jdbc.update("DELETE FROM search_embedding");
        jdbc.update("DELETE FROM search_reindex_checkpoint");
    }
}
