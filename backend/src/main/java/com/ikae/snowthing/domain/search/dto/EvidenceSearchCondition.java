package com.ikae.snowthing.domain.search.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Pattern;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 근거 검색 입력. 쿼리 파라미터를 문자열로 받아 여기서 검증한다. 숫자 변환 실패나 누락을 Spring 기본 예외로 두면 전역 처리기에서 500이 되기 때문이다.
 *
 * @param query 앞뒤 공백을 제거한 검색어 (2~100자)
 * @param size 결과 수 (1~20)
 * @param categoryCode 현재 게시판 코드 필터. null이면 전체
 * @param createdBefore 평가용. 원문 작성 시각(source_created_at, KST)이 이 시각보다 앞선 문서만. null이면 제한 없음
 * @param excludePostPublicId 평가용. 이 게시글과 그 댓글을 결과에서 뺀다(질문 자신의 글타래). null이면 제외 없음
 */
public record EvidenceSearchCondition(
        String query,
        int size,
        String categoryCode,
        LocalDateTime createdBefore,
        String excludePostPublicId) {

    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_QUERY_LENGTH = 100;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 20;

    private static final Pattern CATEGORY_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,49}$");
    private static final Pattern PUBLIC_ID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final DateTimeFormatter KST_SECONDS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);

    public static EvidenceSearchCondition of(String q, String size, String categoryCode) {
        return of(q, size, categoryCode, null, null);
    }

    public static EvidenceSearchCondition of(
            String q,
            String size,
            String categoryCode,
            String createdBefore,
            String excludePostPublicId) {
        String query = q == null ? "" : q.strip();
        int queryLength = query.codePointCount(0, query.length());
        if (queryLength < MIN_QUERY_LENGTH || queryLength > MAX_QUERY_LENGTH) {
            throw invalid();
        }
        return new EvidenceSearchCondition(
                query,
                parseSize(size),
                parseCategory(categoryCode),
                parseCreatedBefore(createdBefore),
                parsePublicId(excludePostPublicId));
    }

    /** KST 기준 {@code YYYY-MM-DDTHH:mm:ss}만 받는다. 시간대 표기·밀리초·날짜만 있는 값은 오류다. */
    private static LocalDateTime parseCreatedBefore(String createdBefore) {
        if (createdBefore == null || createdBefore.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(createdBefore.strip(), KST_SECONDS);
        } catch (DateTimeParseException e) {
            throw invalid();
        }
    }

    private static String parsePublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        String id = publicId.strip().toLowerCase(Locale.ROOT);
        if (!PUBLIC_ID.matcher(id).matches()) {
            throw invalid();
        }
        return id;
    }

    private static int parseSize(String size) {
        if (size == null || size.isBlank()) {
            return DEFAULT_SIZE;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(size.strip());
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (parsed < 1 || parsed > MAX_SIZE) {
            throw invalid();
        }
        return parsed;
    }

    private static String parseCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        String code = categoryCode.strip().toUpperCase(Locale.ROOT);
        if (!CATEGORY_CODE.matcher(code).matches()) {
            throw invalid();
        }
        return code;
    }

    private static CustomException invalid() {
        return new CustomException(ErrorCode.SEARCH_INVALID_REQUEST);
    }
}
