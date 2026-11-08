package com.ikae.snowthing.domain.search.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 근거 식별자. 형식은 {@code P:{post_id}:{chunk_no}} 또는 {@code C:{comment_id}:0}.
 *
 * <p>식별자는 위치만 가리킨다. 권한을 대신하지 않으므로 조회할 때마다 원문 공개 범위를 다시 검사한다.
 */
public record EvidenceId(SearchSourceType sourceType, long sourceId, int chunkNo) {

    private static final Pattern FORMAT =
            Pattern.compile("^([PC]):([1-9][0-9]{0,18}):(0|[1-9][0-9]{0,3})$");

    public static EvidenceId of(SearchSourceType sourceType, long sourceId, int chunkNo) {
        return new EvidenceId(sourceType, sourceId, chunkNo);
    }

    public static EvidenceId parse(String raw) {
        Matcher matcher = raw == null ? null : FORMAT.matcher(raw);
        if (matcher == null || !matcher.matches()) {
            throw new CustomException(ErrorCode.SEARCH_INVALID_REQUEST);
        }
        SearchSourceType type = SearchSourceType.fromEvidencePrefix(matcher.group(1));
        long sourceId;
        try {
            sourceId = Long.parseLong(matcher.group(2));
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.SEARCH_INVALID_REQUEST);
        }
        int chunkNo = Integer.parseInt(matcher.group(3));
        if (type == SearchSourceType.COMMENT && chunkNo != SearchTextChunker.COMMENT_CHUNK_NO) {
            throw new CustomException(ErrorCode.SEARCH_INVALID_REQUEST);
        }
        return new EvidenceId(type, sourceId, chunkNo);
    }

    public String value() {
        return sourceType.evidencePrefix() + ":" + sourceId + ":" + chunkNo;
    }
}
