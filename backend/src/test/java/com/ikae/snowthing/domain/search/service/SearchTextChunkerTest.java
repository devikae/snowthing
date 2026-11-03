package com.ikae.snowthing.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchTextChunkerTest {

    @Test
    @DisplayName("게시글은 0번 구간이 제목이고 본문은 1번부터 시작한다")
    void titleIsChunkZero() {
        List<SearchChunk> chunks = SearchTextChunker.chunkPost("  바인딩   각도 질문 ", "본문 첫 문단");

        assertThat(chunks).extracting(SearchChunk::chunkNo).containsExactly(0, 1);
        assertThat(chunks.get(0).text()).isEqualTo("바인딩 각도 질문");
        assertThat(chunks.get(1).text()).isEqualTo("본문 첫 문단");
    }

    @Test
    @DisplayName("본문이 비어 있으면 제목 구간만 만든다")
    void emptyBodyKeepsOnlyTitle() {
        assertThat(SearchTextChunker.chunkPost("제목", "  \n\n ")).hasSize(1);
        assertThat(SearchTextChunker.chunkPost("제목", null)).hasSize(1);
    }

    @Test
    @DisplayName("짧은 문단은 빈 줄로 이어 붙여 800자 이하 구간 하나로 묶는다")
    void packsShortParagraphs() {
        String body = "첫 문단\n줄바꿈 포함\n\n\n둘째 문단\n\n셋째 문단";

        List<SearchChunk> chunks = SearchTextChunker.chunkPost("제목", body);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1).text()).isEqualTo("첫 문단 줄바꿈 포함\n\n둘째 문단\n\n셋째 문단");
    }

    @Test
    @DisplayName("800자를 넘기는 경계에서 다음 구간으로 넘어간다")
    void startsNewChunkAtBoundary() {
        String first = "가".repeat(500);
        String second = "나".repeat(299); // 500 + 2(구분자) + 299 = 801
        String third = "다".repeat(10);

        List<String> packed = SearchTextChunker.packParagraphs(List.of(first, second, third));

        assertThat(packed).containsExactly(first, second + "\n\n" + third);
        assertThat(packed)
                .allSatisfy(
                        text ->
                                assertThat(text.length())
                                        .isLessThanOrEqualTo(
                                                SearchTextChunker.MAX_BODY_CHUNK_LENGTH));
    }

    @Test
    @DisplayName("정확히 800자인 구간은 나누지 않는다")
    void exactLimitStaysInOneChunk() {
        String first = "가".repeat(400);
        String second = "나".repeat(398); // 400 + 2 + 398 = 800

        assertThat(SearchTextChunker.packParagraphs(List.of(first, second)))
                .containsExactly(first + "\n\n" + second);
    }

    @Test
    @DisplayName("공백 없는 긴 문단은 800자 단위로 강제로 자른다")
    void hardSplitsLongParagraphWithoutSpaces() {
        String paragraph = "가".repeat(1700);

        List<SearchChunk> chunks = SearchTextChunker.chunkPost("제목", "앞 문단\n\n" + paragraph);

        assertThat(chunks).extracting(SearchChunk::chunkNo).containsExactly(0, 1, 2, 3, 4);
        assertThat(chunks.get(1).text()).isEqualTo("앞 문단");
        assertThat(chunks.get(2).text()).hasSize(800);
        assertThat(chunks.get(3).text()).hasSize(800);
        assertThat(chunks.get(4).text()).hasSize(100);
    }

    @Test
    @DisplayName("긴 문단은 뒤쪽 절반 안의 공백이 있으면 단어 경계에서 자른다")
    void splitsLongParagraphAtWhitespace() {
        String paragraph = ("바인딩 ".repeat(300)).strip(); // 4글자 단위, 약 1,199자

        List<String> pieces = SearchTextChunker.splitLongParagraph(paragraph);

        assertThat(pieces).hasSizeGreaterThan(1);
        assertThat(pieces).allSatisfy(piece -> assertThat(piece).doesNotStartWith(" "));
        assertThat(pieces).allSatisfy(piece -> assertThat(piece).doesNotEndWith(" "));
        assertThat(pieces).allSatisfy(piece -> assertThat(piece.length()).isLessThanOrEqualTo(800));
        assertThat(String.join(" ", pieces)).isEqualTo(paragraph);
    }

    @Test
    @DisplayName("서로게이트 쌍은 자르는 위치에서 반으로 나누지 않는다")
    void doesNotSplitSurrogatePair() {
        String paragraph = "가".repeat(799) + "😀" + "나".repeat(10);

        List<String> pieces = SearchTextChunker.splitLongParagraph(paragraph);

        assertThat(pieces.get(0)).isEqualTo("가".repeat(799));
        assertThat(pieces.get(1)).startsWith("😀");
    }

    @Test
    @DisplayName("Markdown 이미지·링크·강조와 HTML 태그를 걷어내고 공백을 합친다")
    void normalizesMarkdownAndHtml() {
        String body =
                "## 왁싱 방법\n"
                        + "**핫왁스**를 [이 글](https://example.com/a)처럼 바른다.<br>다음 줄\n\n"
                        + "![스크래퍼 사진](https://images.snowthing.org/public/posts/x.png)\n\n"
                        + "<p>태그&nbsp;안의 글</p> &lt;b&gt;는 글자";

        List<SearchChunk> chunks = SearchTextChunker.chunkPost("제목", body);

        assertThat(chunks.get(1).text())
                .isEqualTo("왁싱 방법 핫왁스를 이 글처럼 바른다. 다음 줄\n\n스크래퍼 사진\n\n태그 안의 글\n\n<b>는 글자");
        assertThat(chunks.get(1).text()).doesNotContain("https://", "**", "<p>", "&nbsp;");
    }

    @Test
    @DisplayName("같은 내용이면 줄바꿈 방식·공백·유니코드 정규화 형태가 달라도 해시가 같다")
    void hashIsStableAcrossEquivalentInput() {
        String nfc = "바인딩 각도\n\n전경 15도";
        String variant = Normalizer.normalize("바인딩   각도\r\n\r\n전경\t15도  ", Normalizer.Form.NFD);

        SearchChunk a = SearchTextChunker.chunkPost("제목", nfc).get(1);
        SearchChunk b = SearchTextChunker.chunkPost("제목", variant).get(1);

        assertThat(a.hash()).isEqualTo(b.hash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(a.hash()).isEqualTo(SearchTextChunker.sha256Hex(a.text()));
        assertThat(SearchTextChunker.chunkPost("제목", "다른 내용").get(1).hash()).isNotEqualTo(a.hash());
    }

    @Test
    @DisplayName("댓글은 태그를 지우지 않고 공백만 정리한 1구간이며, 비어 있으면 색인하지 않는다")
    void commentIsSingleChunk() {
        List<SearchChunk> chunks = SearchTextChunker.chunkComment("  <b>는 태그가 아니라 글자\n\n두 번째   줄 ");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkNo()).isZero();
        assertThat(chunks.get(0).text()).isEqualTo("<b>는 태그가 아니라 글자\n\n두 번째 줄");
        assertThat(SearchTextChunker.chunkComment("   ")).isEmpty();
    }
}
