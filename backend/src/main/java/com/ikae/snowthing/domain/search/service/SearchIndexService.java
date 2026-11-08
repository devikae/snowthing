package com.ikae.snowthing.domain.search.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.search.dto.SearchRebuildResponse;
import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository.NewSearchDocument;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository.CommentSource;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository.PostSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * search_document 전체 재구축 (1단계).
 *
 * <p>공개 게시글과 그 글의 삭제되지 않은 댓글만 색인한다. 지우기와 넣기를 한 트랜잭션으로 묶어 재구축 도중 검색이 빈 결과를 보지 않게 한다 (FULLTEXT는 커밋된
 * 행만 보므로 커밋 전까지는 이전 색인이 검색된다). 현재 규모(게시글 수백 건 이하)에서는 원문 전체를 한 번에 읽어도 부담이 작다. 규모가 커지면 2단계 증분 갱신과 세대
 * 전환 재인덱싱으로 바꾼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private final SearchSourceRepository sources;
    private final SearchDocumentJdbcRepository documents;
    private final Clock clock;

    @Transactional
    public SearchRebuildResponse rebuild() {
        int removed = documents.deleteAll();
        LocalDateTime indexedAt = LocalDateTime.now(clock);

        List<NewSearchDocument> rows = new ArrayList<>();
        List<PostSource> posts = sources.findVisiblePosts();
        for (PostSource post : posts) {
            for (SearchChunk chunk : SearchTextChunker.chunkPost(post.title(), post.content())) {
                rows.add(toDocument(post, chunk, indexedAt));
            }
        }
        int postChunkCount = rows.size();

        int commentCount = 0;
        for (CommentSource comment : sources.findVisibleComments()) {
            for (SearchChunk chunk : SearchTextChunker.chunkComment(comment.content())) {
                rows.add(toDocument(comment, chunk, indexedAt));
                commentCount++;
            }
        }

        documents.insertAll(rows);
        log.info(
                "search_document rebuilt: posts={}, postChunks={}, comments={}, removed={}",
                posts.size(),
                postChunkCount,
                commentCount,
                removed);
        return new SearchRebuildResponse(
                posts.size(), postChunkCount, commentCount, rows.size(), removed, indexedAt);
    }

    static NewSearchDocument toDocument(
            PostSource post, SearchChunk chunk, LocalDateTime indexedAt) {
        boolean titleChunk = chunk.chunkNo() == SearchTextChunker.TITLE_CHUNK_NO;
        return new NewSearchDocument(
                EvidenceId.of(SearchSourceType.POST, post.postId(), chunk.chunkNo()).value(),
                SearchSourceType.POST,
                post.postId(),
                chunk.chunkNo(),
                post.postId(),
                post.publicId(),
                null,
                post.categoryCode(),
                post.contentVersion(),
                chunk.hash(),
                titleChunk ? chunk.text() : null,
                titleChunk ? "" : chunk.text(),
                post.createdAt(),
                post.updatedAt(),
                indexedAt);
    }

    static NewSearchDocument toDocument(
            CommentSource comment, SearchChunk chunk, LocalDateTime indexedAt) {
        return new NewSearchDocument(
                EvidenceId.of(SearchSourceType.COMMENT, comment.commentId(), chunk.chunkNo())
                        .value(),
                SearchSourceType.COMMENT,
                comment.commentId(),
                chunk.chunkNo(),
                comment.postId(),
                comment.postPublicId(),
                comment.parentCommentId(),
                comment.categoryCode(),
                comment.version(),
                chunk.hash(),
                null,
                chunk.text(),
                comment.createdAt(),
                comment.updatedAt(),
                indexedAt);
    }
}
