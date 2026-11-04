package com.ikae.snowthing.domain.search.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.search.dto.EvidenceDetailResponse;
import com.ikae.snowthing.domain.search.dto.EvidenceSearchCondition;
import com.ikae.snowthing.domain.search.dto.EvidenceSearchItemResponse;
import com.ikae.snowthing.domain.search.dto.EvidenceSearchResponse;
import com.ikae.snowthing.domain.search.dto.SearchMode;
import com.ikae.snowthing.domain.search.dto.SearchUsage;
import com.ikae.snowthing.domain.search.entity.SearchDocument;
import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository.EvidenceSearchRow;
import com.ikae.snowthing.domain.search.repository.SearchDocumentRepository;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository.CommentSource;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository.PostSource;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/** 근거 검색(search_evidence)과 근거 조회(get_evidence). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceService {

    static final int MAX_EXCERPT_LENGTH = 200;
    static final int MAX_EVIDENCE_TEXT_LENGTH = 1000;

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final SearchDocumentJdbcRepository searchDocuments;
    private final SearchDocumentRepository documentRepository;
    private final SearchSourceRepository sources;
    private final HybridEvidenceSearch hybridSearch;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EvidenceSearchResponse search(EvidenceSearchCondition condition) {
        return search(condition, SearchMode.KEYWORD);
    }

    /**
     * 키워드(기본)는 1단계와 같다. 하이브리드와 재정렬은 {@link HybridEvidenceSearch}가 같은 공개 범위와 조건으로 후보를 만든다. 의미 검색이 꺼져 있으면
     * SEARCH_002이고 제공자를 부르지 않는다.
     *
     * <p>트랜잭션 없이 실행한다. 하이브리드는 외부 제공자를 부르므로 그동안 DB 연결과 스냅샷을 잡지 않는다. 각 쿼리는 단일 SELECT다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EvidenceSearchResponse search(EvidenceSearchCondition condition, SearchMode mode) {
        if (mode != SearchMode.KEYWORD) {
            HybridEvidenceSearch.Result result = hybridSearch.search(condition, mode);
            return new EvidenceSearchResponse(
                    condition.query(),
                    condition.size(),
                    mode,
                    result.usage(),
                    result.rows().stream().map(EvidenceService::toItem).toList());
        }
        List<EvidenceSearchRow> rows = keywordRows(condition, condition.size());
        return new EvidenceSearchResponse(
                condition.query(),
                condition.size(),
                SearchMode.KEYWORD,
                SearchUsage.keyword(rows.size()),
                rows.stream().map(EvidenceService::toItem).toList());
    }

    private List<EvidenceSearchRow> keywordRows(EvidenceSearchCondition condition, int limit) {
        return searchDocuments.search(
                condition.query(),
                condition.categoryCode(),
                condition.createdBefore(),
                condition.excludePostPublicId(),
                limit);
    }

    /**
     * 원문을 다시 읽고 공개 범위를 다시 검사한 뒤 구간을 다시 나눈다. 검색 결과의 해시를 expectedHash로 넘기면 그 사이 원문이 바뀌었는지 확인할 수 있다.
     * 공개 범위 검사가 해시 비교보다 먼저다. 볼 수 없는 원문이면 해시 일치 여부도 알려주지 않는다.
     */
    public EvidenceDetailResponse getEvidence(String rawEvidenceId, String expectedHash) {
        EvidenceId evidenceId = EvidenceId.parse(rawEvidenceId);
        String expected = normalizeExpectedHash(expectedHash);

        CurrentChunk current =
                evidenceId.sourceType() == SearchSourceType.POST
                        ? currentPostChunk(evidenceId)
                        : currentCommentChunk(evidenceId);

        if (expected != null && !expected.equals(current.chunk().hash())) {
            throw new CustomException(ErrorCode.EVIDENCE_HASH_MISMATCH);
        }

        Optional<SearchDocument> indexed = documentRepository.findByEvidenceId(evidenceId.value());
        String indexedHash = indexed.map(SearchDocument::getChunkHash).orElse(null);
        boolean stale = indexedHash == null || !indexedHash.equals(current.chunk().hash());
        Truncated text = truncate(current.chunk().text(), MAX_EVIDENCE_TEXT_LENGTH);

        return new EvidenceDetailResponse(
                evidenceId.value(),
                evidenceId.sourceType(),
                current.postPublicId(),
                current.commentId(),
                current.parentCommentId(),
                evidenceId.chunkNo(),
                current.sourceVersion(),
                current.chunk().hash(),
                indexedHash,
                stale,
                EvidenceUrls.postUrl(current.categoryCode(), current.postPublicId()),
                current.postTitle(),
                text.value(),
                text.truncated(),
                current.createdAt(),
                current.updatedAt(),
                indexed.map(SearchDocument::getIndexedAt).orElse(null));
    }

    private CurrentChunk currentPostChunk(EvidenceId evidenceId) {
        PostSource post =
                sources.findVisiblePost(evidenceId.sourceId())
                        .orElseThrow(EvidenceService::notFound);
        SearchChunk chunk =
                SearchTextChunker.chunkPost(post.title(), post.content()).stream()
                        .filter(candidate -> candidate.chunkNo() == evidenceId.chunkNo())
                        .findFirst()
                        .orElseThrow(EvidenceService::notFound);
        return new CurrentChunk(
                chunk,
                post.publicId(),
                null,
                null,
                post.contentVersion(),
                post.categoryCode(),
                post.title(),
                post.createdAt(),
                post.updatedAt());
    }

    private CurrentChunk currentCommentChunk(EvidenceId evidenceId) {
        CommentSource comment =
                sources.findVisibleComment(evidenceId.sourceId())
                        .orElseThrow(EvidenceService::notFound);
        SearchChunk chunk =
                SearchTextChunker.chunkComment(comment.content()).stream()
                        .findFirst()
                        .orElseThrow(EvidenceService::notFound);
        return new CurrentChunk(
                chunk,
                comment.postPublicId(),
                comment.commentId(),
                comment.parentCommentId(),
                comment.version(),
                comment.categoryCode(),
                comment.postTitle(),
                comment.createdAt(),
                comment.updatedAt());
    }

    private static EvidenceSearchItemResponse toItem(EvidenceSearchRow row) {
        String indexedText = row.indexedTitle() != null ? row.indexedTitle() : row.indexedBody();
        Truncated excerpt = truncate(indexedText, MAX_EXCERPT_LENGTH);
        boolean comment = row.sourceType() == SearchSourceType.COMMENT;
        return new EvidenceSearchItemResponse(
                row.evidenceId(),
                row.sourceType(),
                row.postPublicId(),
                comment ? row.sourceId() : null,
                row.parentCommentId(),
                row.chunkNo(),
                row.sourceVersion(),
                row.chunkHash(),
                EvidenceUrls.postUrl(row.categoryCode(), row.postPublicId()),
                row.postTitle(),
                excerpt.value(),
                excerpt.truncated(),
                row.sourceCreatedAt(),
                row.sourceUpdatedAt(),
                row.indexedAt());
    }

    private static String normalizeExpectedHash(String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return null;
        }
        String hash = expectedHash.strip();
        if (!SHA256_HEX.matcher(hash).matches()) {
            throw new CustomException(ErrorCode.SEARCH_INVALID_REQUEST);
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    /** 최대 길이로 자른다. 이모지 같은 서로게이트 쌍은 반으로 자르지 않는다. */
    static Truncated truncate(String text, int maxLength) {
        if (text == null) {
            return new Truncated("", false);
        }
        if (text.length() <= maxLength) {
            return new Truncated(text, false);
        }
        int end = maxLength;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return new Truncated(text.substring(0, end), true);
    }

    private static CustomException notFound() {
        return new CustomException(ErrorCode.EVIDENCE_NOT_FOUND);
    }

    record Truncated(String value, boolean truncated) {}

    private record CurrentChunk(
            SearchChunk chunk,
            String postPublicId,
            Long commentId,
            Long parentCommentId,
            long sourceVersion,
            String categoryCode,
            String postTitle,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}
}
