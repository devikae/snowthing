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
 * 근거 검색용 문서. 원문(post, comment)에서 다시 만들 수 있는 복제본이다.
 *
 * <p>쓰기와 FULLTEXT 검색은 {@code SearchDocumentJdbcRepository}가 JDBC로 한다. 이 엔티티는 스키마 정의(테스트의
 * create-drop, 운영의 validate)와 evidence_id 단건 조회에 쓴다. FULLTEXT ngram 인덱스는 JPA로 표현할 수 없어 운영은 027
 * 마이그레이션, 테스트는 별도 준비 코드가 만든다.
 */
@Entity
@Table(
        name = "search_document",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_search_document_evidence",
                        columnNames = {"evidence_id"}),
        indexes = {
            @Index(name = "idx_search_document_post", columnList = "post_id"),
            @Index(name = "idx_search_document_source", columnList = "source_type,source_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long id;

    @Column(name = "evidence_id", nullable = false, length = 80)
    private String evidenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SearchSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "chunk_no", nullable = false)
    private int chunkNo;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "post_public_id", nullable = false, length = 36)
    private String postPublicId;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "chunk_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String chunkHash;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "source_created_at", nullable = false)
    private LocalDateTime sourceCreatedAt;

    @Column(name = "source_updated_at", nullable = false)
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "indexed_at", nullable = false)
    private LocalDateTime indexedAt;
}
