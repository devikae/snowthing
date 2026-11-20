package com.ikae.snowthing.global.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * Markdown 본문(원시 HTML이 섞인 Toast UI 형식)을 저장하기 전에 정화한다.
 *
 * <p>본문을 HTML로 바꿔 다시 쓰지 않고, 글자를 지우지도 않는다. 위험한 문법의 첫 글자를 같은 글자로 보이는 엔티티로 바꿔 글자로만 읽히게 하고, 나머지는 그대로
 * 둔다. 화면의 DOMPurify(frontend/app/lib/sanitizeHtml.ts)가 마지막 방어선이고, 여기서는 그 정화가 빠진 경로에서도 스크립트가 실행되지 않게
 * 한다.
 *
 * <ul>
 *   <li>코드 블록(``` 또는 ~~~)과 한 줄짜리 인라인 코드 안의 글자는 건드리지 않는다. 범위는 CommonMark 파서가 정한다. 화면의 Toast UI 파서가
 *       다르게 읽을 수 있는 모양(네 칸 들여쓴 코드, 짝 없는 백틱이 있는 글의 인라인 코드 등)은 보호하지 않는다.
 *   <li>코드 밖의 {@code <}가 태그의 시작이면, 한 줄 안에서 문법이 맞고 허용 목록에 있는 태그만 남긴다. 그 밖의 태그 시작(script, iframe, 주석,
 *       닫지 않은 태그 등)은 {@code <}를 {@code &lt;}로 바꾼다. 태그가 글자로 보이고 브라우저는 태그로 읽지 못한다.
 *   <li>남긴 태그에서는 이벤트 속성(on으로 시작)과 위험한 주소가 든 속성만 뺀다.
 *   <li>Markdown 링크와 이미지의 주소가 위험한 스킴이면 여는 괄호를 엔티티로 바꿔 링크가 되지 않게 한다.
 *   <li>코드 블록을 여는 줄의 언어 이름에서 따옴표와 꺾쇠를 빼고, 이미지 대체 글자의 큰따옴표를 한 번 더 감싼다(Toast UI 뷰어가 두 값을 이스케이프 없이 속성에
 *       넣는다).
 * </ul>
 *
 * 결과를 다시 넣어도 바뀌지 않는다(더 바뀌지 않을 때까지 반복한 값을 돌려준다).
 */
public final class MarkdownBodySanitizer {

    private static final int MAX_PASSES = 8;

    private static final Parser PARSER =
            Parser.builder()
                    .extensions(List.of(TablesExtension.create()))
                    .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                    .build();

    /** 화면 정화기(DOMPurify 기본 HTML 목록에서 FORBID_TAGS를 뺀 것) 가운데 문서 구조, 입력 양식 태그를 뺀 목록. */
    private static final Set<String> ALLOWED_TAGS =
            Set.of(
                    "a",
                    "abbr",
                    "acronym",
                    "address",
                    "area",
                    "article",
                    "aside",
                    "audio",
                    "b",
                    "bdi",
                    "bdo",
                    "big",
                    "blink",
                    "blockquote",
                    "br",
                    "caption",
                    "center",
                    "cite",
                    "code",
                    "col",
                    "colgroup",
                    "data",
                    "dd",
                    "del",
                    "details",
                    "dfn",
                    "dir",
                    "div",
                    "dl",
                    "dt",
                    "em",
                    "fieldset",
                    "figcaption",
                    "figure",
                    "font",
                    "footer",
                    "h1",
                    "h2",
                    "h3",
                    "h4",
                    "h5",
                    "h6",
                    "header",
                    "hgroup",
                    "hr",
                    "i",
                    "img",
                    "ins",
                    "kbd",
                    "label",
                    "legend",
                    "li",
                    "main",
                    "map",
                    "mark",
                    "marquee",
                    "menu",
                    "meter",
                    "nav",
                    "nobr",
                    "ol",
                    "p",
                    "picture",
                    "pre",
                    "progress",
                    "q",
                    "rp",
                    "rt",
                    "ruby",
                    "s",
                    "samp",
                    "section",
                    "small",
                    "source",
                    "spacer",
                    "span",
                    "strike",
                    "strong",
                    "sub",
                    "summary",
                    "sup",
                    "table",
                    "tbody",
                    "td",
                    "tfoot",
                    "th",
                    "thead",
                    "time",
                    "tr",
                    "track",
                    "tt",
                    "u",
                    "ul",
                    "var",
                    "video",
                    "wbr");

    private static final Pattern AUTOLINK_SCHEME =
            Pattern.compile("[A-Za-z][A-Za-z0-9+.-]{1,31}:.*");
    private static final Pattern AUTOLINK_EMAIL =
            Pattern.compile(
                    "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+){0,10}");
    private static final int MAX_EMAIL_LENGTH = 254;
    // 자동 링크가 HTML 블록 안에 놓이면 브라우저는 태그로 읽고 '/' 뒤를 속성으로 본다.
    private static final Pattern AUTOLINK_EVENT_ATTRIBUTE = Pattern.compile("(?i)/on[a-z]+=");
    private static final Pattern SAFE_IMAGE_DATA =
            Pattern.compile("^data:image/(?:png|jpe?g|gif|webp|avif|bmp|x-icon)[;,].*");
    private static final Pattern DANGEROUS_STYLE =
            Pattern.compile(
                    "(?i)expression\\s*\\(|javascript:|vbscript:|behavior\\s*:|-moz-binding");

    private static final Pattern FENCE_INFO_UNSAFE = Pattern.compile("[\"'<>&\\\\]");
    private static final Pattern QUOTE_ENTITY = Pattern.compile("&(?:quot|QUOT|#0*34|#[xX]0*22);");
    private static final Pattern BACKSLASH_ESCAPE = Pattern.compile("\\\\(\\p{Punct})");

    private static final char BACKSLASH = 92;
    private static final int MAX_BLOCK_PREFIX = 200;

    private MarkdownBodySanitizer() {}

    public static String sanitize(String markdown) {
        if (markdown == null || !mayNeedSanitizing(markdown)) {
            return markdown;
        }
        String current = markdown;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            String next = sanitizeOnce(current);
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
        // 고칠수록 새 태그가 드러나게 겹겹이 짠 입력이다. 남은 '<'를 모두 글자로 바꿔 끝낸다.
        return sanitizeOnce(current.replace("<", "&lt;"));
    }

    /** 태그('<'), 링크(']'), 코드 블록 여는 줄이 하나도 없으면 고칠 것이 없다. 대부분의 글은 여기서 끝난다. */
    private static boolean mayNeedSanitizing(String markdown) {
        return markdown.indexOf('<') >= 0
                || markdown.indexOf(']') >= 0
                || markdown.contains("```")
                || markdown.contains("~~~");
    }

    private record Edit(int start, int end, String replacement) {}

    private static String sanitizeOnce(String text) {
        BitSet code = codeRanges(text);
        List<Edit> edits = new ArrayList<>();
        tagEdits(text, code, edits);
        linkEdits(text, code, edits);
        imageAltEdits(text, code, edits);
        fenceInfoEdits(text, edits);
        if (edits.isEmpty()) {
            return text;
        }
        edits.sort(Comparator.comparingInt(Edit::start));
        StringBuilder out = new StringBuilder(text.length() + 16);
        int position = 0;
        for (Edit edit : edits) {
            if (edit.start() < position) {
                continue; // 앞의 수정과 겹친다. 다음 반복에서 다시 본다.
            }
            out.append(text, position, edit.start()).append(edit.replacement());
            position = edit.end();
        }
        return out.append(text, position, text.length()).toString();
    }

    // ---------- 코드 범위 ----------

    /**
     * 코드 블록과 인라인 코드의 위치. 화면의 Toast UI 파서와 이 파서가 코드 범위를 다르게 읽는다고 알려진 입력에서는 빈 값을 돌려 코드 안까지 모두 살핀다.
     * 그때는 코드 안의 허용되지 않은 태그가 {@code &lt;}로 보이지만 실행되는 쪽으로 어긋나지는 않는다.
     */
    private static BitSet codeRanges(String text) {
        BitSet code = new BitSet();
        if (parsersMayDisagree(text)) {
            return code;
        }
        BitSet inline = new BitSet();
        try {
            markCode(text, lineStarts(text), code, inline);
        } catch (RuntimeException | StackOverflowError parserFailure) {
            // 파서나 순회가 버티지 못하는 입력이면 코드 보호 없이 전부 살핀다.
            return new BitSet();
        }
        if (!allFenceLinesInside(text, code)) {
            return new BitSet();
        }
        if (allBackticksPaired(text, code, inline)) {
            code.or(inline);
        }
        return code;
    }

    /**
     * 코드 블록 시작처럼 생긴 줄이 모두 파서가 읽은 코드 블록 안에 있는가. Toast UI는 img, br 같은 태그만 있는 줄을 HTML 블록으로 보지 않아, 이
     * 파서가 HTML 블록 안의 글자로 넘긴 ``` 줄을 코드 블록 시작으로 읽는다. 그러면 뒤따르는 코드 블록의 열고 닫는 짝이 서로 어긋난다.
     */
    private static boolean allFenceLinesInside(String text, BitSet fenced) {
        int length = text.length();
        int lineStart = 0;
        while (lineStart < length) {
            int lineEnd = lineStart;
            while (lineEnd < length && !isLineBreak(text.charAt(lineEnd))) {
                lineEnd++;
            }
            int p = lineStart;
            while (p < lineEnd && isBlockPrefixChar(text.charAt(p))) {
                p++;
            }
            if (p + 2 < lineEnd + 1 && isFenceRun(text, p, lineEnd) && !fenced.get(p)) {
                return false;
            }
            lineStart = lineEnd + 1;
        }
        return true;
    }

    private static boolean isFenceRun(String text, int at, int lineEnd) {
        if (at + 2 >= lineEnd) {
            return false;
        }
        char c = text.charAt(at);
        return (c == '`' || c == '~') && text.charAt(at + 1) == c && text.charAt(at + 2) == c;
    }

    /**
     * 인라인 코드는 본문에 표와 코드 밖의 태그가 없고 모든 백틱이 한 줄 안에서 짝을 이룰 때만 보호한다. 짝 없는 백틱이나 여러 줄에 걸친 인라인 코드가 하나라도
     * 있으면, Toast UI가 문단을 다르게 나누는 입력(표, 빈 제목 등)에서 짝이 밀려 코드 안에 있던 글자가 HTML이 된다.
     */
    private static boolean allBackticksPaired(String text, BitSet fenced, BitSet inline) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (fenced.get(i) || inline.get(i)) {
                continue;
            }
            // '|'가 있으면 Toast UI가 그 줄을 표로 읽어 칸마다 따로 해석할 수 있다. 표를 읽는 규칙이 이 파서보다 느슨하다.
            // 코드 밖에 태그가 있으면 그 줄부터 HTML 블록인지 문단인지가 두 파서에서 갈릴 수 있다.
            if (c == '|' || c == '`' || (c == '<' && startsMarkup(text, i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsMarkup(String text, int at) {
        if (at + 1 >= text.length()) {
            return false;
        }
        char next = text.charAt(at + 1);
        return isLetter(next) || next == '/' || next == '!' || next == '?';
    }

    /** 파서가 읽은 구조를 Toast UI도 같게 읽는다고 믿기 어려운 모양. 코드 보호 없이 전부 살핀다. */
    private static final class UntrustedStructure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UntrustedStructure() {
            super(null, null, false, false);
        }
    }

    /**
     * 코드 블록을 여는 줄 바로 앞이 빈 줄(또는 글의 처음)인가. 앞 줄에 붙어 있으면 그 줄이 HTML 블록인지 문단인지에 따라 코드 블록 여부가 갈리는데, 이 판정이
     * Toast UI와 다르다. 편집기는 블록 사이에 빈 줄을 넣으므로 정상 글은 해당하지 않는다.
     */
    private static boolean followsBlankLine(String text, int[] lineStarts, int lineIndex) {
        if (lineIndex == 0) {
            return true;
        }
        for (int p = lineStarts[lineIndex - 1]; p < lineStarts[lineIndex]; p++) {
            char c = text.charAt(p);
            if (c != ' ' && c != '\t' && c != '>' && !isLineBreak(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDeeplyIndented(String text, int lineStart) {
        int p = lineStart;
        while (p < text.length() && text.charAt(p) == ' ' && p - lineStart < 4) {
            p++;
        }
        return p - lineStart >= 4 || (p < text.length() && text.charAt(p) == '\t');
    }

    private static void markCode(String text, int[] lineStarts, BitSet fenced, BitSet inline) {
        PARSER.parse(text)
                .accept(
                        new AbstractVisitor() {
                            @Override
                            public void visit(FencedCodeBlock node) {
                                List<SourceSpan> spans = node.getSourceSpans();
                                if (!spans.isEmpty()
                                        && !followsBlankLine(
                                                text, lineStarts, spans.get(0).getLineIndex())) {
                                    throw new UntrustedStructure();
                                }
                                mark(node, fenced);
                            }

                            @Override
                            public void visit(Code node) {
                                // '|'가 든 인라인 코드는 Toast UI가 표의 칸으로 나누면 끊긴다.
                                // 네 칸 넘게 들여쓴 줄은 Toast UI가 문단이 아닌 다른 블록으로 읽는 경우가 있다.
                                List<SourceSpan> spans = node.getSourceSpans();
                                if (spans.size() == 1
                                        && !isDeeplyIndented(
                                                text, lineStarts[spans.get(0).getLineIndex()])) {
                                    mark(node, inline);
                                }
                            }

                            private void mark(Node node, BitSet target) {
                                for (SourceSpan span : node.getSourceSpans()) {
                                    int from =
                                            lineStarts[span.getLineIndex()] + span.getColumnIndex();
                                    target.set(from, from + span.getLength());
                                }
                            }
                        });
    }

    /**
     * Toast UI 파서(JavaScript)는 줄바꿈 없는 공백(U+00A0) 같은 문자도 공백으로 보고, commonmark-java는 백틱과 물결이 붙은 줄을 코드
     * 블록의 시작으로 보지 않는다. 이런 글자가 있으면 두 파서의 코드 범위가 달라질 수 있다. 파서에 부담을 주는 입력도 여기서 거른다.
     */
    private static boolean parsersMayDisagree(String text) {
        if (text.contains("```~") || text.contains("~~~`")) {
            return true;
        }
        int prefix = 0;
        boolean inPrefix = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 ? (c != '\n' && c != '\r' && c != '\t') : isUnicodeSpace(c)) {
                return true;
            }
            if (isLineBreak(c)) {
                prefix = 0;
                inPrefix = true;
            } else if (inPrefix && isBlockPrefixChar(c)) {
                // 인용이나 목록을 한 줄에 수백 겹 쌓으면 파서가 겹 수의 제곱만큼 시간을 쓴다. 정상 글에는 없는 모양이라 파서를 쓰지 않는다.
                if (++prefix > MAX_BLOCK_PREFIX) {
                    return true;
                }
            } else {
                inPrefix = false;
            }
        }
        return false;
    }

    private static boolean isUnicodeSpace(char c) {
        return c == 0x7f
                || c == 0x85
                || c == 0xa0
                || c == 0x1680
                || (c >= 0x2000 && c <= 0x200a)
                || c == 0x2028
                || c == 0x2029
                || c == 0x202f
                || c == 0x205f
                || c == 0x3000
                || c == 0xfeff;
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                starts.add(i + 1);
            } else if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    // ---------- Markdown 링크와 코드 블록 여는 줄 ----------

    /**
     * {@code [글자](주소)}와 {@code [이름]: 주소}의 주소가 위험하면 괄호나 쌍점을 엔티티로 바꾼다. 파서의 링크 판정에 기대지 않고 글자 모양만
     * 본다(HTML 블록으로 볼지 문단으로 볼지가 파서마다 달라, 한쪽에서만 링크가 되는 입력이 있다).
     */
    private static void linkEdits(String text, BitSet code, List<Edit> edits) {
        int at = text.indexOf(']');
        while (at >= 0 && at + 1 < text.length()) {
            char marker = text.charAt(at + 1);
            if ((marker == '(' || marker == ':')
                    && !code.get(at)
                    && isDangerousUrl(destination(text, at + 2))) {
                edits.add(new Edit(at + 1, at + 2, marker == '(' ? "&#40;" : "&#58;"));
            }
            at = text.indexOf(']', at + 1);
        }
    }

    /**
     * {@code ![대체 글자](주소)}의 대체 글자에 든 큰따옴표를 한 번 더 감싼다. Toast UI 뷰어는 대체 글자의 엔티티를 풀고 이스케이프 없이 alt 속성에
     * 넣어, 따옴표가 있으면 속성을 빠져나와 이벤트 속성이 된다. {@code &amp;quot;}로 두면 풀린 뒤에도 {@code &quot;}라 속성 안에 머문다.
     */
    private static void imageAltEdits(String text, BitSet code, List<Edit> edits) {
        int at = text.indexOf("![");
        while (at >= 0) {
            if (!code.get(at)) {
                int depth = 0;
                for (int p = at + 1; p < text.length(); p++) {
                    char c = text.charAt(p);
                    if (c == '[') {
                        depth++;
                    } else if (c == ']' && --depth == 0) {
                        break;
                    } else if (c == '"') {
                        edits.add(new Edit(p, p + 1, "&amp;quot;"));
                    } else if (c == '&'
                            && QUOTE_ENTITY.matcher(text).region(p, text.length()).lookingAt()) {
                        edits.add(new Edit(p, p + 1, "&amp;"));
                    } else if (isLineBreak(c) && isBlankAfter(text, p)) {
                        break;
                    }
                }
            }
            at = text.indexOf("![", at + 2);
        }
    }

    /** p의 줄바꿈 뒤가 빈 줄이거나 글의 끝인가(문단이 끝나는가). */
    private static boolean isBlankAfter(String text, int p) {
        int q = p + 1;
        if (text.charAt(p) == '\r' && q < text.length() && text.charAt(q) == '\n') {
            q++;
        }
        while (q < text.length() && (text.charAt(q) == ' ' || text.charAt(q) == '\t')) {
            q++;
        }
        return q >= text.length() || isLineBreak(text.charAt(q));
    }

    /** from부터 읽은 링크 주소. 엔티티와 역슬래시 이스케이프를 Markdown처럼 풀어서 돌려준다. */
    private static String destination(String text, int from) {
        int p = from;
        while (p < text.length() && Character.isWhitespace(text.charAt(p))) {
            p++;
        }
        int end = p;
        if (p < text.length() && text.charAt(p) == '<') {
            p++;
            end = p;
            while (end < text.length()
                    && end - p < 256
                    && text.charAt(end) != '>'
                    && !isLineBreak(text.charAt(end))) {
                end++;
            }
        } else {
            while (end < text.length()
                    && end - p < 256
                    && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
        }
        String unescaped = BACKSLASH_ESCAPE.matcher(text.substring(p, end)).replaceAll("$1");
        return org.jsoup.parser.Parser.unescapeEntities(unescaped, false);
    }

    /**
     * 코드 블록을 여는 줄(앞에 공백, 인용, 목록 표시가 올 수 있다)의 언어 이름에서 따옴표와 꺾쇠를 뺀다. Toast UI 뷰어는 언어 이름을 이스케이프하지 않고
     * 속성(data-language)에 넣어, 따옴표가 있으면 속성을 빠져나와 태그가 된다.
     */
    private static void fenceInfoEdits(String text, List<Edit> edits) {
        int length = text.length();
        int lineStart = 0;
        while (lineStart < length) {
            int lineEnd = lineStart;
            while (lineEnd < length && !isLineBreak(text.charAt(lineEnd))) {
                lineEnd++;
            }
            int p = lineStart;
            while (p < lineEnd && isBlockPrefixChar(text.charAt(p))) {
                p++;
            }
            if (p + 2 < lineEnd && (text.charAt(p) == '`' || text.charAt(p) == '~')) {
                char fence = text.charAt(p);
                int infoStart = p;
                while (infoStart < lineEnd && text.charAt(infoStart) == fence) {
                    infoStart++;
                }
                if (infoStart - p >= 3) {
                    String info = text.substring(infoStart, lineEnd);
                    String safe = FENCE_INFO_UNSAFE.matcher(info).replaceAll("");
                    if (!safe.equals(info)) {
                        edits.add(new Edit(infoStart, lineEnd, safe));
                    }
                }
            }
            lineStart = lineEnd + 1;
        }
    }

    /** 줄 맨 앞에서 코드 블록 시작보다 먼저 올 수 있는 글자: 들여쓰기, 인용(>), 목록 표시(-, +, *, 숫자와 . 또는 )). */
    private static boolean isBlockPrefixChar(char c) {
        return c == ' '
                || c == '\t'
                || c == '>'
                || c == '-'
                || c == '+'
                || c == '*'
                || c == '.'
                || c == ')'
                || (c >= '0' && c <= '9');
    }

    static boolean isDangerousUrl(String value) {
        if (value == null) {
            return false;
        }
        StringBuilder compact = new StringBuilder(Math.min(value.length(), 64));
        for (int i = 0; i < value.length() && compact.length() < 64; i++) {
            char c = value.charAt(i);
            // 브라우저는 주소 안의 탭과 줄바꿈, 앞쪽의 제어 문자와 공백을 버리고 스킴을 읽는다.
            if (c > 0x20 && !isUnicodeSpace(c)) {
                compact.append(Character.toLowerCase(c));
            }
        }
        String url = compact.toString();
        if (url.startsWith("javascript:")
                || url.startsWith("vbscript:")
                || url.startsWith("livescript:")) {
            return true;
        }
        return url.startsWith("data:") && !SAFE_IMAGE_DATA.matcher(url).matches();
    }

    // ---------- 원시 HTML 태그 ----------

    private record Attribute(int start, int end, String name, String rawValue) {}

    private record Tag(int end, String name, List<Attribute> attributes) {}

    private static void tagEdits(String text, BitSet code, List<Edit> edits) {
        int length = text.length();
        int i = text.indexOf('<');
        while (i >= 0 && i + 1 < length) {
            if (!code.get(i)) {
                char c = text.charAt(i + 1);
                if (isLetter(c)) {
                    openTagOrAutolink(text, i, edits);
                } else if (c == '/' && i + 2 < length && isLetter(text.charAt(i + 2))) {
                    if (!isAllowedCloseTag(text, i)) {
                        edits.add(escape(text, i));
                    }
                } else if (c == '!' || c == '?') {
                    // 주석, 선언, 처리 명령. 브라우저마다 끝을 읽는 규칙이 달라 글자로 바꾼다.
                    edits.add(escape(text, i));
                }
            }
            // 남긴 태그의 속성값 안에 있는 '<'도 따로 살피도록 한 글자씩만 넘어간다.
            i = text.indexOf('<', i + 1);
        }
    }

    /**
     * 태그를 지우지 않고 '<'만 글자로 바꾼다. 지우면 뒤의 글자가 줄 맨 앞으로 와 목록이나 인용으로 바뀔 수 있다.
     *
     * <p>앞에 이스케이프용 역슬래시가 있으면(편집기가 글자 '<'를 그렇게 저장한다) 역슬래시까지 함께 바꾼다. 남겨 두면 역슬래시가 엔티티의 '&'를 이스케이프해 화면에
     * "&lt;"가 그대로 보인다.
     */
    private static Edit escape(String text, int at) {
        int backslashes = 0;
        while (at - backslashes > 0 && text.charAt(at - backslashes - 1) == BACKSLASH) {
            backslashes++;
        }
        return new Edit(backslashes % 2 == 1 ? at - 1 : at, at + 1, "&lt;");
    }

    private static void openTagOrAutolink(String text, int at, List<Edit> edits) {
        Tag tag = parseOpenTag(text, at);
        if (tag == null) {
            if (!isSafeAutolink(text, at)) {
                edits.add(escape(text, at));
            }
            return;
        }
        if (!ALLOWED_TAGS.contains(tag.name())) {
            edits.add(escape(text, at));
            return;
        }
        StringBuilder rebuilt = null;
        int copied = at;
        for (Attribute attribute : tag.attributes()) {
            if (isDangerousAttribute(attribute)) {
                rebuilt = rebuilt == null ? new StringBuilder() : rebuilt;
                rebuilt.append(text, copied, attribute.start());
                copied = attribute.end();
            }
        }
        if (rebuilt != null) {
            edits.add(new Edit(at, tag.end(), rebuilt.append(text, copied, tag.end()).toString()));
        }
    }

    /** {@code <https://...>}, {@code <a@b.c>} 모양의 자동 링크이고 주소가 위험하지 않은가. */
    private static boolean isSafeAutolink(String text, int at) {
        int end = at + 1;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == '>') {
                break;
            }
            if (c <= 0x20 || c == '<' || isUnicodeSpace(c)) {
                return false;
            }
            end++;
        }
        if (end >= text.length()) {
            return false;
        }
        String body = text.substring(at + 1, end);
        boolean autolink =
                AUTOLINK_SCHEME.matcher(body).matches()
                        || (body.length() <= MAX_EMAIL_LENGTH
                                && AUTOLINK_EMAIL.matcher(body).matches());
        return autolink && !isDangerousUrl(body) && !AUTOLINK_EVENT_ATTRIBUTE.matcher(body).find();
    }

    /** 닫는 태그는 이름 뒤에 공백과 '>'만 오는 모양이고 허용 목록의 이름일 때만 남긴다. */
    private static boolean isAllowedCloseTag(String text, int at) {
        int p = at + 2;
        while (p < text.length() && isNameChar(text.charAt(p))) {
            p++;
        }
        String name = text.substring(at + 2, p).toLowerCase(Locale.ROOT);
        p = skipBlanks(text, p);
        return p < text.length() && text.charAt(p) == '>' && ALLOWED_TAGS.contains(name);
    }

    private static boolean isDangerousAttribute(Attribute attribute) {
        String name = attribute.name();
        if (name.startsWith("on") || name.equals("srcdoc") || name.equals("formaction")) {
            return true;
        }
        if (attribute.rawValue() == null) {
            return false;
        }
        String value = org.jsoup.parser.Parser.unescapeEntities(attribute.rawValue(), true);
        if (name.equals("style")) {
            return DANGEROUS_STYLE.matcher(value).find();
        }
        return isDangerousUrl(value);
    }

    /**
     * CommonMark의 여는 태그 문법을 한 줄 안에서만 인정한다. 줄이 바뀌는 태그는 Markdown이 앞뒤를 다르게 처리할 수 있어(인용, 목록의 경계) 브라우저가
     * 읽는 범위와 어긋난다.
     */
    private static Tag parseOpenTag(String text, int at) {
        int length = text.length();
        int p = at + 1;
        while (p < length && isNameChar(text.charAt(p))) {
            p++;
        }
        String name = text.substring(at + 1, p).toLowerCase(Locale.ROOT);
        List<Attribute> attributes = new ArrayList<>();
        while (true) {
            int afterBlanks = skipBlanks(text, p);
            if (afterBlanks >= length) {
                return null;
            }
            char c = text.charAt(afterBlanks);
            if (c == '>') {
                return new Tag(afterBlanks + 1, name, attributes);
            }
            if (c == '/' && afterBlanks + 1 < length && text.charAt(afterBlanks + 1) == '>') {
                return new Tag(afterBlanks + 2, name, attributes);
            }
            if (afterBlanks == p || !(isLetter(c) || c == '_' || c == ':')) {
                return null;
            }
            int nameEnd = afterBlanks + 1;
            while (nameEnd < length && isAttributeNameChar(text.charAt(nameEnd))) {
                nameEnd++;
            }
            String attributeName = text.substring(afterBlanks, nameEnd).toLowerCase(Locale.ROOT);
            int valueStart = skipBlanks(text, nameEnd);
            if (valueStart >= length || text.charAt(valueStart) != '=') {
                attributes.add(new Attribute(p, nameEnd, attributeName, null));
                p = nameEnd;
                continue;
            }
            valueStart = skipBlanks(text, valueStart + 1);
            if (valueStart >= length) {
                return null;
            }
            char quote = text.charAt(valueStart);
            int valueEnd;
            String rawValue;
            if (quote == '"' || quote == '\'') {
                int close = valueStart + 1;
                while (close < length && text.charAt(close) != quote) {
                    if (isLineBreak(text.charAt(close))) {
                        return null;
                    }
                    close++;
                }
                if (close >= length) {
                    return null;
                }
                rawValue = text.substring(valueStart + 1, close);
                valueEnd = close + 1;
            } else {
                valueEnd = valueStart;
                while (valueEnd < length && isUnquotedValueChar(text.charAt(valueEnd))) {
                    valueEnd++;
                }
                if (valueEnd == valueStart) {
                    return null;
                }
                rawValue = text.substring(valueStart, valueEnd);
            }
            attributes.add(new Attribute(p, valueEnd, attributeName, rawValue));
            p = valueEnd;
        }
    }

    private static int skipBlanks(String text, int from) {
        int p = from;
        while (p < text.length() && (text.charAt(p) == ' ' || text.charAt(p) == '\t')) {
            p++;
        }
        return p;
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isNameChar(char c) {
        return isLetter(c) || (c >= '0' && c <= '9') || c == '-';
    }

    private static boolean isAttributeNameChar(char c) {
        return isNameChar(c) || c == '_' || c == '.' || c == ':';
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r';
    }

    private static boolean isUnquotedValueChar(char c) {
        return c > 0x20 && c != '"' && c != '\'' && c != '=' && c != '<' && c != '>' && c != '`';
    }
}
