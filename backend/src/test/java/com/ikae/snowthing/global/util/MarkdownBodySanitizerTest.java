package com.ikae.snowthing.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class MarkdownBodySanitizerTest {

    private static String sanitize(String markdown) {
        String result = MarkdownBodySanitizer.sanitize(markdown);
        // 정화한 값을 다시 넣어도 바뀌지 않아야 한다(수정 화면에서 그대로 다시 저장하는 경우).
        assertThat(MarkdownBodySanitizer.sanitize(result)).isEqualTo(result);
        return result;
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("정상 Markdown과 허용된 HTML은 글자 하나 바뀌지 않는다")
    @ValueSource(
            strings = {
                "그냥 글입니다.\n\n두 번째 문단",
                "<p>첫 줄</p>\n<p>둘째 줄<br>셋째 줄</p>",
                "<p><br></p>\n\n<br/>\n<br />",
                "> 인용\n> 둘째 줄\n>> 겹친 인용",
                "속도는 a < b 이고 b > c, 3<5, x<=y, <3 하트, <- 화살표, <홍길동> 님",
                "# 제목\n\n**굵게** *기울임* ~~취소선~~ [링크](https://snowthing.org/posts/1 \"제목\")",
                "![사진](https://images.snowthing.org/public/posts/a.jpg)",
                "![붙여넣은 사진](data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==)",
                "<img src=\"data:image/jpeg;base64,/9j/4AAQSkZJRg==\" alt=\"사진\" width=\"300\">",
                "<img src=\"https://images.snowthing.org/a.jpg\" alt=\"사진\" contenteditable=\"false\">",
                "| 리조트 | 개장 |\n| --- | --- |\n| 용평 | 11/21<br>예정 |\n| 휘닉스 | `a < b` |",
                "- [ ] 왁싱\n- [x] 엣지 정비\n\n1. 하나\n2. 둘",
                "자동 링크 <https://snowthing.org/posts?a=1&b=2> 와 <help@snowthing.org>",
                "엔티티 &amp; &lt;b&gt; &nbsp; &#60;script&#62; &copy; AT&T",
                "```html\n<script>alert(1)</script>\n<img src=x onerror=alert(1)>\n```",
                "~~~\n<iframe src=\"javascript:alert(1)\"></iframe>\n~~~",
                "인라인 코드 `<script>alert(1)</script>` 와 ``<a href=\"javascript:x\" onclick=1>`` 예시",
                "- 목록\n\n  ```\n  <script>목록 안의 코드 블록</script>\n  ```",
                "<span style=\"color: rgb(255, 0, 0);\">빨간 글자</span> <u>밑줄</u> <ins>삽입</ins>",
                "<div align=\"center\"><b and c>굵게</b></div>",
                "<details><summary>펼치기</summary>\n\n내용\n\n</details>",
                "<a href=\"https://snowthing.org\" target=\"_blank\" rel=\"noopener noreferrer\">링크</a>",
                "<a href=\"/posts/1\">상대 주소</a> <a href=\"mailto:a@b.co\">메일</a> <a href=\"tel:010\">전화</a>",
                "[참조 링크][1]\n\n[1]: https://snowthing.org \"눈팅\"",
                "javascript: 는 언어 이름이고 data: 는 그냥 글자다. onclick=이라고 써도 글자다.",
                "줄 끝 역슬래시\\\n각주 같은 글자 [1] 과 배열 a[0]: 값",
                "\\`백틱\\` 이스케이프와 \\*별표\\*",
                "<video src=\"https://images.snowthing.org/a.mp4\" controls></video>",
                "윈도우 줄바꿈\r\n<p>둘째 줄</p>\r\n\r\n`<b>`\r\n\r\n```\r\n<script>x</script>\r\n```\r\n",
                "> 인용 안의 코드 블록\n>\n> ```\n> <script>x</script>\n> ```",
            })
    void keepsNormalBody(String markdown) {
        assertThat(sanitize(markdown)).isEqualTo(markdown);
    }

    static Stream<Arguments> attacks() {
        return Stream.of(
                Arguments.of(
                        "script 태그",
                        "앞<script>alert(1)</script>뒤",
                        "앞&lt;script>alert(1)&lt;/script>뒤"),
                Arguments.of(
                        "대소문자 섞은 script",
                        "앞<ScRiPt src=//evil.example/x.js></sCrIpT >뒤",
                        "앞&lt;ScRiPt src=//evil.example/x.js>&lt;/sCrIpT >뒤"),
                Arguments.of("닫지 않은 script", "앞<script>alert(1)", "앞&lt;script>alert(1)"),
                Arguments.of("img onerror", "<img src=x onerror=alert(1)>", "<img src=x>"),
                Arguments.of(
                        "따옴표 속 > 뒤의 이벤트 속성",
                        "<img title=\"a>b\" src=\"x\" OnError=\"alert(1)\">",
                        "<img title=\"a>b\" src=\"x\">"),
                Arguments.of(
                        "슬래시로 붙인 이벤트 속성",
                        "<img/src=x/onerror=alert(1)>",
                        "&lt;img/src=x/onerror=alert(1)>"),
                Arguments.of(
                        "svg onload",
                        "<svg onload=alert(1)><circle r=1 /></svg>",
                        "&lt;svg onload=alert(1)>&lt;circle r=1 />&lt;/svg>"),
                Arguments.of(
                        "svg 안의 script",
                        "<svg><script>alert(1)</script></svg>",
                        "&lt;svg>&lt;script>alert(1)&lt;/script>&lt;/svg>"),
                Arguments.of(
                        "math",
                        "<math><mi xlink:href=\"javascript:alert(1)\">x</mi></math>",
                        "&lt;math>&lt;mi xlink:href=\"javascript:alert(1)\">x&lt;/mi>&lt;/math>"),
                Arguments.of(
                        "iframe",
                        "<iframe src=\"https://evil.example\"></iframe>끝",
                        "&lt;iframe src=\"https://evil.example\">&lt;/iframe>끝"),
                Arguments.of(
                        "iframe srcdoc",
                        "<iframe srcdoc=\"<script>alert(1)</script>\">",
                        "&lt;iframe srcdoc=\"&lt;script>alert(1)&lt;/script>\">"),
                Arguments.of(
                        "style 태그",
                        "<style>body{display:none}</style>글",
                        "&lt;style>body{display:none}&lt;/style>글"),
                Arguments.of(
                        "object와 embed",
                        "<object data=x></object><embed src=x>글",
                        "&lt;object data=x>&lt;/object>&lt;embed src=x>글"),
                Arguments.of(
                        "meta와 base와 link",
                        "<meta http-equiv=refresh content=\"0;url=//e\"><base href=//e><link rel=stylesheet href=//e>글",
                        "&lt;meta http-equiv=refresh content=\"0;url=//e\">&lt;base href=//e>&lt;link rel=stylesheet href=//e>글"),
                Arguments.of(
                        "form과 input",
                        "<form action=//e><input name=pw><button>전송</button></form>",
                        "&lt;form action=//e>&lt;input name=pw>&lt;button>전송&lt;/button>&lt;/form>"),
                Arguments.of(
                        "javascript: 링크", "<a href=\"javascript:alert(1)\">눌러</a>", "<a>눌러</a>"),
                Arguments.of("대소문자 섞은 스킴", "<a href=\"JaVaScRiPt:alert(1)\">눌러</a>", "<a>눌러</a>"),
                Arguments.of(
                        "엔티티로 숨긴 스킴",
                        "<a href=\"&#106;avascript&colon;alert(1)\">눌러</a>",
                        "<a>눌러</a>"),
                Arguments.of(
                        "16진 엔티티와 세미콜론 없는 엔티티",
                        "<a href=\"&#x6A&#97vascript:alert(1)\">눌러</a>",
                        "<a>눌러</a>"),
                Arguments.of(
                        "탭과 줄바꿈 엔티티를 끼운 스킴",
                        "<a href=\" java&Tab;scr&#10;ipt:alert(1)\">눌러</a>",
                        "<a>눌러</a>"),
                Arguments.of(
                        "앞에 제어 문자를 둔 스킴",
                        "<a href=\"&#1; javascript:alert(1)\">눌러</a>",
                        "<a>눌러</a>"),
                Arguments.of("따옴표 없는 스킴", "<a href=javascript:alert(1)>눌러</a>", "<a>눌러</a>"),
                Arguments.of("vbscript", "<a href='vbscript:msgbox(1)'>눌러</a>", "<a>눌러</a>"),
                Arguments.of(
                        "data: html 링크",
                        "<a href=\"data:text/html;base64,PHNjcmlwdD4=\">눌러</a>",
                        "<a>눌러</a>"),
                Arguments.of(
                        "data: svg 이미지",
                        "<img alt=\"그림\" src=\"data:image/svg+xml;base64,PHN2Zz4=\">",
                        "<img alt=\"그림\">"),
                Arguments.of(
                        "style 속성의 expression",
                        "<p style=\"width:expression(alert(1))\">글</p>",
                        "<p>글</p>"),
                Arguments.of(
                        "Markdown 링크",
                        "[눌러](javascript:alert(1))",
                        "[눌러]&#40;javascript:alert(1))"),
                Arguments.of(
                        "Markdown 링크, 대소문자와 엔티티와 이스케이프",
                        "[하나](JAVASCRIPT&colon;alert(1)) [둘](&#106;avascript:alert(1)) [셋](javascript\\:alert(1))",
                        "[하나]&#40;JAVASCRIPT&colon;alert(1)) [둘]&#40;&#106;avascript:alert(1)) [셋]&#40;javascript\\:alert(1))"),
                Arguments.of(
                        "Markdown 링크, 꺾쇠 주소와 탭",
                        "[눌러](<java\tscript:alert(1)> \"제목\")",
                        "[눌러]&#40;&lt;java\tscript:alert(1)> \"제목\")"),
                Arguments.of(
                        "Markdown 링크, 괄호 뒤 줄바꿈",
                        "[눌러](\n  javascript:alert(1))",
                        "[눌러]&#40;\n  javascript:alert(1))"),
                Arguments.of(
                        "Markdown 이미지",
                        "![그림](javascript:alert(1))",
                        "![그림]&#40;javascript:alert(1))"),
                Arguments.of(
                        "Markdown data: 링크",
                        "[눌러](data:text/html;base64,PHNjcmlwdD4=)",
                        "[눌러]&#40;data:text/html;base64,PHNjcmlwdD4=)"),
                Arguments.of(
                        "참조 링크 정의",
                        "[눌러][x]\n\n[x]: javascript:alert(1)",
                        "[눌러][x]\n\n[x]&#58; javascript:alert(1)"),
                Arguments.of(
                        "br 줄 뒤의 링크(Toast UI는 문단으로, CommonMark는 HTML 블록으로 읽는다)",
                        "<br>\n[눌러](javascript:alert(1))",
                        "<br>\n[눌러]&#40;javascript:alert(1))"),
                Arguments.of("자동 링크", "<javascript:alert(1)>", "&lt;javascript:alert(1)>"),
                Arguments.of(
                        "자동 링크 모양의 태그",
                        "<div>\n<ab:/onmouseover=alert(1)//>글\n</div>",
                        "<div>\n&lt;ab:/onmouseover=alert(1)//>글\n</div>"),
                Arguments.of(
                        "표 칸 안의 링크",
                        "| a |\n| --- |\n| [눌러](javascript:alert(1)) |",
                        "| a |\n| --- |\n| [눌러]&#40;javascript:alert(1)) |"),
                Arguments.of(
                        "겹쳐 쓴 태그",
                        "<scr<script>ipt>alert(1)</scr</script>ipt>",
                        "&lt;scr&lt;script>ipt>alert(1)&lt;/scr&lt;/script>ipt>"),
                Arguments.of(
                        "꺾쇠를 하나 더 붙인 태그",
                        "<<script>alert(1)</script>",
                        "<&lt;script>alert(1)&lt;/script>"),
                Arguments.of(
                        "닫지 않은 태그",
                        "<img src=x onerror=alert(1) ",
                        "&lt;img src=x onerror=alert(1) "),
                Arguments.of(
                        "닫지 않은 따옴표로 뒤 문단의 링크를 속성으로 만드는 입력",
                        "<div title=\"\n\n[a](onmouseover=alert(1)//)",
                        "&lt;div title=\"\n\n[a](onmouseover=alert(1)//)"),
                Arguments.of(
                        "인용 경계를 넘는 여러 줄 태그",
                        "> <div title=\"\n[a](onmouseover=alert(1)//) \">",
                        "> &lt;div title=\"\n[a](onmouseover=alert(1)//) \">"),
                Arguments.of(
                        "여러 줄 태그",
                        "<img src=x\nonerror=alert(1)>",
                        "&lt;img src=x\nonerror=alert(1)>"),
                Arguments.of(
                        "폼피드로 나눈 속성",
                        "<img src=x\fonerror=alert(1)>",
                        "&lt;img src=x\fonerror=alert(1)>"),
                Arguments.of(
                        "역슬래시로 앞 꺾쇠만 글자로 만든 입력",
                        "\\<a title=\"<script>alert(1)</script>\">",
                        "\\<a title=\"&lt;script>alert(1)&lt;/script>\">"),
                Arguments.of(
                        "HTML 블록 안의 백틱(코드가 아니라 원시 HTML이다)",
                        "<div>\n`<script>alert(1)</script>`\n</div>",
                        "<div>\n`&lt;script>alert(1)&lt;/script>`\n</div>"),
                Arguments.of(
                        "HTML 블록에 이어 붙은 줄의 백틱",
                        "<p>문단</p>\n`<script>alert(1)</script>`",
                        "<p>문단</p>\n`&lt;script>alert(1)&lt;/script>`"),
                Arguments.of(
                        "표 칸 경계로 끊기는 백틱",
                        "| a | b |\n| --- | --- |\n| `x | <img src=x onerror=alert(1)>` |",
                        "| a | b |\n| --- | --- |\n| `x | <img src=x>` |"),
                Arguments.of(
                        "주석",
                        "앞<!-- <script>alert(1)</script> -->뒤",
                        "앞&lt;!-- &lt;script>alert(1)&lt;/script> -->뒤"),
                Arguments.of(
                        "닫지 않은 주석과 선언",
                        "<!-- 주석 <!DOCTYPE html> <?php x ?>",
                        "&lt;!-- 주석 &lt;!DOCTYPE html> &lt;?php x ?>"),
                Arguments.of(
                        "mXSS: noscript",
                        "<noscript><p title=\"</noscript><img src=x onerror=alert(1)>\">",
                        "&lt;noscript><p title=\"&lt;/noscript><img src=x>\">"),
                Arguments.of(
                        "mXSS: style과 주석",
                        "<svg><style><!--</style><img src=x onerror=alert(1)>-->",
                        "&lt;svg>&lt;style>&lt;!--&lt;/style><img src=x>-->"),
                Arguments.of(
                        "mXSS: 닫는 태그의 속성",
                        "</p title=\"><img src=x onerror=alert(1)>\">",
                        "&lt;/p title=\"><img src=x>\">"),
                Arguments.of(
                        "허용 목록에 없는 태그",
                        "<blink2 onclick=alert(1)>글</blink2> <x-foo>사용자 태그</x-foo> <your name>",
                        "&lt;blink2 onclick=alert(1)>글&lt;/blink2> &lt;x-foo>사용자 태그&lt;/x-foo> &lt;your name>"),
                Arguments.of(
                        "textarea 탈출",
                        "<textarea></textarea><img src=x onerror=alert(1)>",
                        "&lt;textarea>&lt;/textarea><img src=x>"),
                Arguments.of(
                        "코드 블록 밖과 안",
                        "```\n<script>안</script>\n```\n<script>밖</script>",
                        "```\n<script>안</script>\n```\n&lt;script>밖&lt;/script>"),
                Arguments.of(
                        "네 칸 들여쓴 줄(Toast UI가 문단으로 읽는 경우가 있어 코드로 보지 않는다)",
                        "> # \n    <img src=x onerror=alert(1)>",
                        "> # \n    <img src=x>"),
                Arguments.of(
                        "여러 줄에 걸친 인라인 코드(표로 읽히면 코드가 끊긴다)",
                        "`a\n|---|---|\n<img src=x onerror=alert(1)>`",
                        "`a\n|---|---|\n<img src=x>`"),
                Arguments.of(
                        "짝 없는 백틱이 앞 줄에 있는 인라인 코드(문단이 합쳐지면 짝이 밀린다)",
                        "짝 없는 ` 백틱\n# \n`<img src=x onerror=alert(1)>`",
                        "짝 없는 ` 백틱\n# \n`<img src=x>`"),
                Arguments.of(
                        "여러 줄 링크 제목이 백틱을 삼키는 입력",
                        "[a](x \"\n|---|---|\n`\") `<img src=x onerror=alert(1)>`",
                        "[a](x \"\n|---|---|\n`\") `<img src=x>`"),
                Arguments.of(
                        "태그만 있는 줄 뒤의 코드 블록(Toast UI와 열고 닫는 짝이 어긋난다)",
                        "<img src=x>\n~~~\n\n~~~\n<img src=x onerror=alert(1)>",
                        "<img src=x>\n~~~\n\n~~~\n<img src=x>"),
                Arguments.of(
                        "앞 줄에 붙은 코드 블록(앞 줄을 HTML 블록으로 읽으면 코드가 아니다)",
                        "> 인용\n<a>\n```\n<img src=x onerror=alert(1)>\n```",
                        "> 인용\n<a>\n```\n<img src=x>\n```"),
                Arguments.of(
                        "이미지 대체 글자의 따옴표로 속성을 빠져나오는 입력",
                        "![a\" onerror=\"alert(1)](https://example.com/a.jpg) ![b&quot; onerror=&#34;alert(1)](x)",
                        "![a&amp;quot; onerror=&amp;quot;alert(1)](https://example.com/a.jpg) ![b&amp;quot; onerror=&amp;#34;alert(1)](x)"),
                Arguments.of(
                        "표가 있는 글의 인라인 코드(표를 읽는 규칙이 달라 보호하지 않는다)",
                        "a | b\n`<img src=x onerror=alert(1)>`",
                        "a | b\n`<img src=x>`"),
                Arguments.of(
                        "코드 밖에 태그가 있는 글의 인라인 코드(HTML 블록 판정이 달라 보호하지 않는다)",
                        "> 인용\n<a>\n- `<img src=x onerror=alert(1)>` 그리고 `<script>`",
                        "> 인용\n<a>\n- `<img src=x>` 그리고 `&lt;script>`"),
                Arguments.of(
                        "편집기가 역슬래시로 이스케이프한 꺾쇠",
                        "\\<script>alert(1)\\</script> \\\\<script>",
                        "&lt;script>alert(1)&lt;/script> \\\\&lt;script>"),
                Arguments.of(
                        "코드 블록 언어 이름으로 속성을 빠져나오는 입력",
                        "```\"><img src=x onerror=alert(1)>\n코드\n```",
                        "```img src=x onerror=alert(1)\n코드\n```"),
                Arguments.of(
                        "코드 블록 언어 이름의 따옴표와 엔티티",
                        "> ~~~js\" onmouseover=\"alert(1)\n\n- ```&quot; onmouseover=&quot;alert(1)",
                        "> ~~~js onmouseover=alert(1)\n\n- ```quot; onmouseover=quot;alert(1)"),
                Arguments.of(
                        "백틱과 물결이 붙은 코드 블록 시작(파서마다 다르게 읽어 코드 안까지 살핀다)",
                        "```~~~\n<script>alert(1)</script>\n```",
                        "```~~~\n&lt;script>alert(1)&lt;/script>\n```"),
                Arguments.of(
                        "줄바꿈 없는 공백으로 HTML 블록을 만드는 입력(코드 안까지 살핀다)",
                        "</pre> \n```\n<img src=x onerror=alert(1)>\n```",
                        "</pre> \n```\n<img src=x>\n```"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("위험한 HTML과 주소는 지우고 나머지 글자는 남긴다")
    @MethodSource("attacks")
    void removesDangerousParts(String name, String markdown, String expected) {
        assertThat(sanitize(markdown)).isEqualTo(expected);
    }

    @Test
    @DisplayName("본문 최대 길이의 꺾쇠 입력도 오래 걸리지 않는다")
    void largeInputIsFast() {
        String markdown = "<script>".repeat(12_500);
        long started = System.nanoTime();

        String result = sanitize(markdown);

        assertThat(result).isEqualTo("&lt;script>".repeat(12_500));
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(5_000);
    }

    @Test
    @DisplayName("겹겹이 짠 입력도 끝나고 태그가 남지 않는다")
    void deeplyNestedInputTerminates() {
        String markdown = "<".repeat(2_000) + "script>".repeat(2_000) + "alert(1)</script>";

        String result = sanitize(markdown);

        assertThat(result.toLowerCase(Locale.ROOT)).doesNotContain("<script");
    }

    static Stream<Arguments> pathological() {
        return Stream.of(
                Arguments.of("인용 표시 10만 개", ">".repeat(100_000) + "<script>alert(1)</script>"),
                Arguments.of("목록 표시 5만 개", "- ".repeat(50_000) + "<script>alert(1)</script>"),
                Arguments.of("대괄호 10만 개", "[".repeat(100_000) + "](javascript:alert(1))"),
                Arguments.of("강조 표시 5만 쌍", "*a_".repeat(33_000) + "<script>alert(1)</script>"),
                Arguments.of(
                        "세로선 10만 개",
                        "|".repeat(50_000) + "\n" + "|-".repeat(25_000) + "\n<script>"),
                Arguments.of("줄마다 깊어지는 목록", nestedList(400) + "<script>alert(1)</script>"),
                Arguments.of("백틱 10만 개", "`".repeat(100_000) + "<script>alert(1)</script>"),
                Arguments.of("닫지 않은 따옴표가 든 태그 1만 개", "<a t='".repeat(10_000)),
                Arguments.of(
                        "점으로 이은 아주 긴 메일 주소",
                        "<a@" + "b.".repeat(49_000) + "c><script>alert(1)</script>"),
                Arguments.of("줄마다 코드 블록 시작 2만 개", "```\"<script>\n".repeat(20_000)));
    }

    private static String nestedList(int depth) {
        StringBuilder markdown = new StringBuilder();
        for (int level = 0; level < depth; level++) {
            markdown.append(" ".repeat(level * 2)).append("- a\n");
        }
        return markdown.toString();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("파서에 부담을 주는 입력도 오류 없이 끝나고 태그가 남지 않는다")
    @MethodSource("pathological")
    void pathologicalInputTerminates(String name, String markdown) {
        long started = System.nanoTime();

        String result = MarkdownBodySanitizer.sanitize(markdown);

        assertThat(result.toLowerCase(Locale.ROOT))
                .doesNotContain("<script")
                .doesNotContain("](javascript:");
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(10_000);
    }

    @Test
    @DisplayName("null과 빈 값은 그대로 돌려준다")
    void nullAndEmpty() {
        assertThat(MarkdownBodySanitizer.sanitize(null)).isNull();
        assertThat(MarkdownBodySanitizer.sanitize("")).isEmpty();
    }
}
