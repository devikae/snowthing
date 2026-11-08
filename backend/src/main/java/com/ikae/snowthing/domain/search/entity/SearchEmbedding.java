package com.ikae.snowthing.domain.search.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 의미 검색 실험(IKA-11)용 구간 벡터. search_document에서 다시 만들 수 있는 복제본이다.
 *
 * <p>쓰기와 검색은 {@code SearchEmbeddingJdbcRepository}가 JDBC로 한다. 이 엔티티는 스키마 정의(테스트의 create-drop, 운영의
 * validate)에만 쓴다. 운영 스키마는 030 마이그레이션이 만든다.
 */
@Entity
@Table(
        name = "search_embedding",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_search_embedding_evidence_model",
                        columnNames = {"evidence_id", "model_id"}),
        indexes = {
            @Index(name = "idx_search_embedding_post", columnList = "post_id"),
            @Index(name = "idx_search_embedding_source", columnList = "source_type,source_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "embedding_id")
    private Long id;

    @Column(name = "evidence_id", nullable = false, length = 80)
    private String evidenceId;

    @Column(name = "model_id", nullable = false, length = 100)
    private String modelId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SearchSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "dimension", nullable = false)
    private int dimension;

    @Column(name = "vector", nullable = false, columnDefinition = "BLOB")
    private byte[] vector;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
