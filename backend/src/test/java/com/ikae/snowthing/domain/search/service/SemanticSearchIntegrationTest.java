package com.ikae.snowthing.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.search.repository.SearchEmbeddingJdbcRepository;
import com.ikae.snowthing.domain.search.semantic.SemanticSearchProperties;
import com.ikae.snowthing.domain.search.semantic.SemanticSearchSupport;
import com.ikae.snowthing.domain.search.semantic.VectorMath;
import com.ikae.snowthing.testsupport.CountingEmbeddingProvider;

/**
 * 의미 검색을 켠 컨텍스트. 제공자는 외부 호출 없는 결정적 테스트 제공자다.
 *
 * <p>속성이 달라 별도 컨텍스트가 생긴다. 로컬 MySQL 연결 수 한도를 넘지 않게 연결 풀을 줄이고 클래스가 끝나면 컨텍스트를 닫는다.
 */
@SpringBootTest(
        properties = {
            "snowthing.search.semantic.enabled=true",
            "spring.datasource.hikari.maximum-pool-size=4",
            // 이 컨텍스트를 닫을 때 create-drop이 공유 테스트 스키마를 지우면 캐시된 다른 컨텍스트의 테스트가 깨진다.
            "spring.jpa.hibernate.ddl-auto=update"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(CountingEmbeddingProvider.Config.class)
class SemanticSearchIntegrationTest extends SemanticSearchTestSupport {

    private static final String QUERY = "바인딩 각도";

    @Autowired private SearchEmbeddingJdbcRepository embeddingRepository;
    @Autowired private SemanticSearchSupport semantic;

    @Test
    @DisplayName("outbox 처리 뒤 모든 구간에 현재 벡터가 생기고, 다시 처리해도 제공자를 다시 부르지 않는다")
    void processingCreatesVectors() {
        Created post = createPost("덕스탠스 세팅 질문", "스노보드 바인딩 각도는 전경 15도에서 시작해 보세요.");
        long comment = createComment(post, null, "바인딩 각도는 +15/-15도도 많이 씁니다.");

        processAll();

        assertThat(embeddedEvidenceIds())
                .containsExactlyInAnyOrder(
                        "P:" + post.id() + ":0", "P:" + post.id() + ":1", "C:" + comment + ":0");
        assertThat(embeddedEvidenceIds()).isEqualTo(documentEvidenceIds());
        for (String id : embeddedEvidenceIds()) {
            assertThat(embeddingHash(id)).isEqualTo(documentHash(id));
        }
        int calls = provider.calls();
        assertThat(calls).isPositive();

        processAll();
        processor.processBatch();
        assertThat(provider.calls()).isEqualTo(calls);
    }

    @Test
    @DisplayName("하이브리드는 키워드에 없는 벡터 후보도 결합하고, 비용 메타데이터와 방식을 돌려준다")
    void hybridFusesKeywordAndVectorCandidates() throws Exception {
        Created binding = createPost("덕스탠스 세팅 질문", "스노보드 바인딩 각도는 전경 15도에서 시작해 보세요.");
        Created wax = createPost("왁스 관리", "시즌 전에 왁스를 바르고 엣지를 정리합니다.");
        processAll();
        provider.reset();

        JsonNode keyword =
                json(
                        search(QUERY, null)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.mode").value("keyword"))
                                .andExpect(jsonPath("$.usage.embeddingCalls").value(0)));
        JsonNode hybrid =
                json(
                        search(QUERY, "hybrid")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.mode").value("hybrid"))
                                .andExpect(jsonPath("$.usage.embeddingCalls").value(1))
                                .andExpect(
                                        jsonPath("$.usage.embeddingInputTokens")
                                                .value(QUERY.length()))
                                .andExpect(jsonPath("$.usage.vectorCandidates").value(4)));

        String waxBody = "P:" + wax.id() + ":1";
        assertThat(evidenceIds(keyword))
                .contains("P:" + binding.id() + ":1")
                .doesNotContain(waxBody);
        assertThat(evidenceIds(hybrid)).contains("P:" + binding.id() + ":1", waxBody);
        assertThat(evidenceIds(hybrid).getFirst()).isEqualTo("P:" + binding.id() + ":1");
        assertThat(hybrid.get("items").get(0).has("score")).isFalse();
        assertThat(provider.calls()).isEqualTo(1);
        assertThat(provider.texts()).containsExactly(QUERY);
    }

    @Test
    @DisplayName("하이브리드는 숨김, 삭제, MARKET 글과 삭제 댓글을 벡터 경로로도 돌려주지 않는다")
    void hybridRespectsVisibility() throws Exception {
        Created visible = createPost("덕스탠스 세팅 질문", "스노보드 바인딩 각도는 전경 15도에서 시작해 보세요.");
        long visibleComment = createComment(visible, null, "바인딩 각도는 체형에 따라 다릅니다.");
        long deletedComment = createComment(visible, null, "삭제될 바인딩 각도 댓글");
        Created hidden = createPost("숨김 바인딩 각도", "숨김 처리될 바인딩 각도 글");
        Created deleted = createPost("삭제 바인딩 각도", "삭제될 바인딩 각도 글");
        Created market = createPost("장터 바인딩 각도", "장터로 옮겨질 바인딩 각도 글");
        processAll();
        assertThat(embeddedEvidenceIds()).hasSize(documentEvidenceIds().size()).hasSize(10);

        // outbox를 거치지 않고 원문만 바꾼다. 색인 문서와 벡터는 남아 있어도 조회 시 원문 조인으로 걸러져야 한다.
        long marketCategory =
                categories
                        .findByCode("MARKET")
                        .orElseGet(
                                () ->
                                        categories.save(
                                                PostCategory.builder()
                                                        .code("MARKET")
                                                        .name("Market")
                                                        .build()))
                        .getId();
        transactions.executeWithoutResult(
                status -> {
                    jdbc.update("UPDATE post SET status = 'HIDDEN' WHERE post_id = ?", hidden.id());
                    jdbc.update(
                            "UPDATE post SET is_deleted = true WHERE post_id = ?", deleted.id());
                    jdbc.update(
                            "UPDATE post SET category_id = ? WHERE post_id = ?",
                            marketCategory,
                            market.id());
                    jdbc.update(
                            "UPDATE comment SET is_deleted = true WHERE comment_id = ?",
                            deletedComment);
                });
        assertThat(embeddedEvidenceIds()).hasSize(10);

        JsonNode hybrid =
                json(
                        search(QUERY, "hybrid")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.usage.vectorCandidates").value(3)));

        assertThat(evidenceIds(hybrid))
                .containsExactlyInAnyOrder(
                        "P:" + visible.id() + ":0",
                        "P:" + visible.id() + ":1",
                        "C:" + visibleComment + ":0");
    }

    @Test
    @DisplayName("하이브리드는 createdBefore와 excludePostPublicId를 키워드와 벡터 양쪽에 적용한다")
    void hybridRespectsEvaluationFilters() throws Exception {
        Created old = createPost("예전 바인딩 각도 질문", "예전에 쓴 바인딩 각도 글입니다.");
        Created recent = createPost("최근 바인딩 각도 질문", "최근에 쓴 바인딩 각도 글입니다.");
        processAll();
        transactions.executeWithoutResult(
                status ->
                        jdbc.update(
                                "UPDATE search_document SET source_created_at = ? WHERE post_id = ?",
                                LocalDateTime.of(2026, 1, 1, 0, 0),
                                old.id()));

        JsonNode before =
                json(
                        search(QUERY, "hybrid", "createdBefore", "2026-06-01T00:00:00")
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.usage.vectorCandidates").value(2)));
        assertThat(evidenceIds(before))
                .containsExactlyInAnyOrder("P:" + old.id() + ":0", "P:" + old.id() + ":1");

        JsonNode excluded =
                json(
                        search(QUERY, "hybrid", "excludePostPublicId", recent.publicId())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.usage.vectorCandidates").value(2)));
        assertThat(evidenceIds(excluded)).noneMatch(id -> id.startsWith("P:" + recent.id() + ":"));
    }

    @Test
    @DisplayName("내용이 바뀐 구간만 다시 임베딩하고, 해시가 맞지 않는 벡터는 검색에 쓰지 않고 다시 채운다")
    void staleVectorsAreDetectedAndRefreshed() throws Exception {
        Created post = createPost("덕스탠스 세팅 질문", "바인딩 각도는 전경 15도에서 시작합니다.");
        Created other = createPost("왁스 관리", "시즌 전에 왁스를 바릅니다.");
        processAll();
        String body = "P:" + post.id() + ":1";
        String oldHash = embeddingHash(body);
        provider.reset();

        editPost(post, "덕스탠스 세팅 질문", "바인딩 각도는 전경 18도, 후경 -9도로 바꿨습니다.");
        processAll();

        assertThat(embeddingHash(body)).isNotEqualTo(oldHash).isEqualTo(documentHash(body));
        // 제목 구간은 그대로라 다시 보내지 않는다.
        assertThat(provider.texts()).containsExactly("바인딩 각도는 전경 18도, 후경 -9도로 바꿨습니다.");

        // 해시를 어긋나게 만든 벡터는 "현재 벡터"가 아니다.
        String otherBody = "P:" + other.id() + ":1";
        transactions.executeWithoutResult(
                status ->
                        jdbc.update(
                                "UPDATE search_embedding SET content_hash = ? WHERE evidence_id = ?",
                                "f".repeat(64),
                                otherBody));
        search(QUERY, "hybrid").andExpect(jsonPath("$.usage.vectorCandidates").value(3));
        assertThat(embeddingRepository.findMissing(provider.modelId(), provider.dimension(), 10))
                .extracting(SearchEmbeddingJdbcRepository.MissingEmbedding::evidenceId)
                .containsExactly(otherBody);

        // 처리한 이벤트가 없는 폴링은 유휴 간격(30초) 안이면 조회하지 않는다.
        provider.reset();
        processor.processBatch();
        assertThat(provider.calls()).isZero();
        assertThat(embeddingHash(otherBody)).isEqualTo("f".repeat(64));

        resumeEmbeddingRefresh();
        processor.processBatch();
        assertThat(provider.texts()).containsExactly("시즌 전에 왁스를 바릅니다.");
        assertThat(embeddingHash(otherBody)).isEqualTo(documentHash(otherBody));
        search(QUERY, "hybrid").andExpect(jsonPath("$.usage.vectorCandidates").value(4));
    }

    @Test
    @DisplayName("제공자 호출 사이 구간이 바뀌었으면 옛 해시로 만든 벡터를 저장하지 않는다")
    void doesNotSaveVectorForOutdatedHash() {
        Created post = createPost("덕스탠스 세팅 질문", "바인딩 각도 글");
        processAll();
        String body = "P:" + post.id() + ":1";
        String current = embeddingHash(body);

        boolean saved =
                embeddingRepository.saveIfCurrent(
                        body,
                        "0".repeat(64),
                        provider.modelId(),
                        provider.dimension(),
                        VectorMath.toBytes(new float[provider.dimension()]),
                        LocalDateTime.now());

        assertThat(saved).isFalse();
        assertThat(embeddingHash(body)).isEqualTo(current);
    }

    @Test
    @DisplayName("게시글 삭제는 글과 댓글의 벡터를, 댓글 삭제는 그 댓글의 벡터를 같은 처리에서 지운다")
    void tombstonesRemoveVectors() {
        Created post = createPost("덕스탠스 세팅 질문", "바인딩 각도 글");
        createComment(post, null, "바인딩 각도 댓글");
        Created other = createPost("왁스 관리", "왁스 글");
        long otherComment = createComment(other, null, "왁스 댓글");
        processAll();
        assertThat(embeddedEvidenceIds()).hasSize(6);

        commentService.deleteComment(otherComment, null, details(user));
        processAll();
        assertThat(embeddedEvidenceIds()).doesNotContain("C:" + otherComment + ":0").hasSize(5);

        postService.deletePost(post.publicId(), null, details(user));
        processAll();
        assertThat(embeddedEvidenceIds())
                .containsExactlyInAnyOrder("P:" + other.id() + ":0", "P:" + other.id() + ":1");
    }

    @Test
    @DisplayName("제공자가 실패하면 문서 색인은 끝나고 벡터 갱신만 쉰다. 질문 임베딩 실패는 503 SEARCH_003")
    void providerFailure() throws Exception {
        createPost("덕스탠스 세팅 질문", "바인딩 각도 글");
        provider.failNext();
        processAll();

        assertThat(documentEvidenceIds()).hasSize(2);
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM search_outbox", String.class))
                .containsOnly("DONE");
        assertThat(embeddedEvidenceIds()).isEmpty();
        int calls = provider.calls();
        processor.processBatch();
        assertThat(provider.calls()).isEqualTo(calls); // 쉬는 동안 다시 부르지 않는다

        resumeEmbeddingRefresh();
        processor.processBatch();
        assertThat(embeddedEvidenceIds()).hasSize(2);

        provider.failNext();
        search(QUERY, "hybrid")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEARCH_003"));
    }

    @Test
    @DisplayName("공개 범위를 통과한 벡터가 max-vector-scan을 넘으면 일부만 비교하지 않고 409 SEARCH_002로 거절한다")
    void rejectsWhenVectorScanLimitExceeded() throws Exception {
        createPost("덕스탠스 세팅 질문", "바인딩 각도 글");
        createPost("왁스 관리", "왁스 글");
        processAll();
        assertThat(embeddedEvidenceIds()).hasSize(4);
        SemanticSearchProperties original = semantic.properties();
        try {
            ReflectionTestUtils.setField(semantic, "properties", withMaxVectorScan(original, 3));
            search(QUERY, "hybrid")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SEARCH_002"));

            ReflectionTestUtils.setField(semantic, "properties", withMaxVectorScan(original, 4));
            search(QUERY, "hybrid")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usage.vectorCandidates").value(4));
        } finally {
            ReflectionTestUtils.setField(semantic, "properties", original);
        }
    }

    private static SemanticSearchProperties withMaxVectorScan(
            SemanticSearchProperties p, int maxVectorScan) {
        return new SemanticSearchProperties(
                p.enabled(),
                p.provider(),
                p.rrfK(),
                p.candidateK(),
                p.refreshBatchSize(),
                p.refreshMaxBatches(),
                p.refreshIdleIntervalMs(),
                maxVectorScan,
                p.http());
    }

    @Test
    @DisplayName("재정렬기가 없으면 hybrid_rerank는 409 SEARCH_002, 모르는 방식은 400 SEARCH_001")
    void rerankRequiresReranker() throws Exception {
        search(QUERY, "hybrid_rerank")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEARCH_002"));
        search(QUERY, "vector")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_001"));
        assertThat(provider.calls()).isZero();
    }
}
