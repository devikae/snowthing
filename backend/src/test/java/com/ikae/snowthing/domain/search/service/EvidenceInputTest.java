package com.ikae.snowthing.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ikae.snowthing.domain.search.dto.EvidenceSearchCondition;
import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class EvidenceInputTest {

    @Test
    @DisplayName("근거 ID를 해석하고 같은 문자열로 되돌린다")
    void parsesEvidenceId() {
        EvidenceId post = EvidenceId.parse("P:12:3");
        EvidenceId comment = EvidenceId.parse("C:7:0");

        assertThat(post).isEqualTo(EvidenceId.of(SearchSourceType.POST, 12, 3));
        assertThat(post.value()).isEqualTo("P:12:3");
        assertThat(comment.sourceType()).isEqualTo(SearchSourceType.COMMENT);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "X:1:0",
                "P:0:0",
                "P:01:0",
                "P:1",
                "P:1:-1",
                "P:1:10000",
                "C:1:1",
                "p:1:0",
                "P:99999999999999999999:0",
                "P:9999999999999999999:0"
            })
    @DisplayName("형식이 맞지 않는 근거 ID는 SEARCH_001")
    void rejectsInvalidEvidenceId(String raw) {
        assertThatThrownBy(() -> EvidenceId.parse(raw))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEARCH_INVALID_REQUEST);
    }

    @Test
    @DisplayName("검색어는 앞뒤 공백을 지우고, 결과 수 기본값은 10, 카테고리 코드는 대문자로 맞춘다")
    void normalizesCondition() {
        EvidenceSearchCondition condition = EvidenceSearchCondition.of("  바인딩 각도 ", null, " qna ");

        assertThat(condition.query()).isEqualTo("바인딩 각도");
        assertThat(condition.size()).isEqualTo(10);
        assertThat(condition.categoryCode()).isEqualTo("QNA");
        assertThat(EvidenceSearchCondition.of("ab", "20", null).size()).isEqualTo(20);
        assertThat(EvidenceSearchCondition.of("가".repeat(100), "1", "").categoryCode()).isNull();
    }

    @Test
    @DisplayName("검색어 길이·결과 수·카테고리 형식이 범위를 벗어나면 SEARCH_001")
    void rejectsInvalidCondition() {
        assertInvalid(null, null, null);
        assertInvalid(" 가 ", null, null);
        assertInvalid("가".repeat(101), null, null);
        assertInvalid("바인딩", "0", null);
        assertInvalid("바인딩", "21", null);
        assertInvalid("바인딩", "abc", null);
        assertInvalid("바인딩", null, "QNA;DROP");
    }

    @Test
    @DisplayName("평가용 조건: createdBefore는 KST 초 단위 시각, excludePostPublicId는 소문자 UUID로 맞춘다")
    void parsesEvaluationFilters() {
        String publicId = "0F8FAD5B-D9CB-469F-A165-70867728950E";
        EvidenceSearchCondition condition =
                EvidenceSearchCondition.of("바인딩", null, null, " 2026-10-15T09:30:00 ", publicId);

        assertThat(condition.createdBefore()).isEqualTo(LocalDateTime.of(2026, 10, 15, 9, 30, 0));
        assertThat(condition.excludePostPublicId()).isEqualTo(publicId.toLowerCase(Locale.ROOT));
        EvidenceSearchCondition empty = EvidenceSearchCondition.of("바인딩", null, null, "", " ");
        assertThat(empty.createdBefore()).isNull();
        assertThat(empty.excludePostPublicId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2026-10-15",
                "2026-10-15T09:30",
                "2026-10-15T09:30:00.123",
                "2026-10-15T09:30:00+09:00",
                "2026-10-15T09:30:00Z",
                "2026-02-30T09:30:00",
                "2026-10-15 09:30:00",
                "yesterday"
            })
    @DisplayName("createdBefore 형식이 맞지 않으면 SEARCH_001")
    void rejectsInvalidCreatedBefore(String createdBefore) {
        assertThatThrownBy(() -> EvidenceSearchCondition.of("바인딩", null, null, createdBefore, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEARCH_INVALID_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12", "0f8fad5b-d9cb-469f-a165-70867728950", "P:1:0"})
    @DisplayName("excludePostPublicId가 UUID가 아니면 SEARCH_001")
    void rejectsInvalidExcludePostPublicId(String publicId) {
        assertThatThrownBy(() -> EvidenceSearchCondition.of("바인딩", null, null, null, publicId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEARCH_INVALID_REQUEST);
    }

    private static void assertInvalid(String q, String size, String categoryCode) {
        assertThatThrownBy(() -> EvidenceSearchCondition.of(q, size, categoryCode))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEARCH_INVALID_REQUEST);
    }
}
