package com.ikae.snowthing.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import com.ikae.snowthing.domain.search.semantic.VectorMath;
import com.ikae.snowthing.testsupport.CountingEmbeddingProvider;

/** 기본 설정(의미 검색 꺼짐)에서는 제공자 빈이 있어도 한 번도 부르지 않는다. 운영 기본 동작이 1단계와 같고 비용이 없음을 확인한다. */
@SpringBootTest(
        properties = {
            "snowthing.search.semantic.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=4"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(CountingEmbeddingProvider.Config.class)
class SemanticSearchDisabledIntegrationTest extends SemanticSearchTestSupport {

    @Test
    @DisplayName("꺼져 있으면 작성, 수정, 삭제, 처리, 검색 어디에서도 제공자를 부르지 않고 벡터도 만들지 않는다")
    void disabledNeverCallsProvider() throws Exception {
        Created post = createPost("덕스탠스 세팅 질문", "스노보드 바인딩 각도는 전경 15도에서 시작해 보세요.");
        long comment = createComment(post, null, "바인딩 각도는 +15/-15도도 많이 씁니다.");
        processAll();
        editPost(post, "덕스탠스 세팅 질문", "바인딩 각도를 18도로 바꿨습니다.");
        commentService.deleteComment(comment, null, details(user));
        processAll();
        processor.processBatch();

        search("바인딩 각도", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("keyword"))
                .andExpect(jsonPath("$.usage.embeddingCalls").value(0))
                .andExpect(jsonPath("$.items[0].evidenceId").value("P:" + post.id() + ":1"));
        search("바인딩 각도", "keyword").andExpect(status().isOk());
        search("바인딩 각도", "hybrid")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEARCH_002"));
        search("바인딩 각도", "hybrid_rerank")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEARCH_002"));

        assertThat(provider.calls()).isZero();
        assertThat(embeddedEvidenceIds()).isEmpty();
        assertThat(documentEvidenceIds()).isNotEmpty();
    }

    @Test
    @DisplayName("꺼져 있어도 원문 수정, 삭제로 맞지 않게 된 기존 벡터는 지운다 (제공자 호출 없음)")
    void disabledStillRemovesOutdatedVectors() {
        Created post = createPost("덕스탠스 세팅 질문", "바인딩 각도 글");
        processAll();
        String title = "P:" + post.id() + ":0";
        String body = "P:" + post.id() + ":1";
        // 예전에 켜져 있을 때 만든 벡터가 남아 있는 상황
        insertVector(title, post.id());
        insertVector(body, post.id());

        editPost(post, "덕스탠스 세팅 질문", "바인딩 각도 글을 고쳤습니다.");
        processAll();
        assertThat(embeddedEvidenceIds()).containsExactly(title);

        postService.deletePost(post.publicId(), null, details(user));
        processAll();
        assertThat(embeddedEvidenceIds()).isEmpty();
        assertThat(provider.calls()).isZero();
    }

    private void insertVector(String evidenceId, long postId) {
        transactions.executeWithoutResult(
                status ->
                        jdbc.update(
                                "INSERT INTO search_embedding (evidence_id, model_id, post_id,"
                                        + " source_type, source_id, content_hash, dimension, vector,"
                                        + " created_at) VALUES (?, 'old-model', ?, 'POST', ?, ?, 2, ?, ?)",
                                evidenceId,
                                postId,
                                postId,
                                documentHash(evidenceId),
                                VectorMath.toBytes(new float[] {1, 0}),
                                LocalDateTime.now()));
    }
}
