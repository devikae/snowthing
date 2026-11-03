package com.ikae.snowthing.domain.search.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 게시글·댓글 원문을 검색 구간으로 나눈다.
 *
 * <p>게시글 본문은 Toast UI Editor의 {@code getMarkdown()} 결과(Markdown, 일부 인라인 HTML 가능)로 저장된다. 이미지·링크 주소와
 * 태그는 검색어와 무관하고 발췌를 지저분하게 만들기 때문에 걷어낸 뒤 공백을 정리한다. 같은 원문은 항상 같은 구간과 해시를 만들어야 하므로 이 클래스는 상태가 없고 입력만으로
 * 결과가 정해진다.
 */
public final class SearchTextChunker {

    /** 게시글 본문 구간의 목표 최대 길이(문자 수). */
    public static final int MAX_BODY_CHUNK_LENGTH = 800;

    public static final int TITLE_CHUNK_NO = 0;
    public static final int COMMENT_CHUNK_NO = 0;

    private static final String PARAGRAPH_SEPARATOR = "\n\n";

    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    private static final Pattern HTML_LINE_BREAK =
            Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_BLOCK_TAG =
            Pattern.compile(
                    "</?(p|div|li|ul|ol|h[1-6]|blockquote|pre|table|tr)(\\s[^>]*)?>",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern LINE_PREFIX = Pattern.compile("(?m)^[ \\t]*(#{1,6}|>+)[ \\t]+");
    private static final Pattern EMPHASIS_MARK = Pattern.compile("\\*\\*|__|~~|`");
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n[ \\t\\u00A0]*\\n+");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    private SearchTextChunker() {}

    /** 게시글을 0번(제목) + 1번부터 본문 구간으로 나눈다. 제목은 항상 0번 구간으로 남긴다. */
    public static List<SearchChunk> chunkPost(String title, String content) {
        List<SearchChunk> chunks = new ArrayList<>();
        chunks.add(chunk(TITLE_CHUNK_NO, collapse(nfc(title))));
        int chunkNo = TITLE_CHUNK_NO + 1;
        for (String text : packParagraphs(markdownParagraphs(content))) {
            chunks.add(chunk(chunkNo++, text));
        }
        return List.copyOf(chunks);
    }

    /**
     * 댓글은 1건 = 1구간. 댓글은 일반 텍스트로 저장·표시되므로 Markdown·태그 제거를 하지 않고 공백만 정리한다. 정규화 결과가 비면 색인할 내용이 없으므로 빈
     * 목록을 돌려준다.
     */
    public static List<SearchChunk> chunkComment(String content) {
        String text = String.join(PARAGRAPH_SEPARATOR, splitParagraphs(lineBreaks(nfc(content))));
        if (text.isEmpty()) {
            return List.of();
        }
        return List.of(chunk(COMMENT_CHUNK_NO, text));
    }

    /** 게시글 본문(Markdown)을 정규화한 뒤 빈 줄 기준 문단 목록으로 만든다. 문단 안의 공백·줄바꿈은 공백 하나로 합친다. */
    static List<String> markdownParagraphs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String text = lineBreaks(nfc(raw));
        text = MARKDOWN_IMAGE.matcher(text).replaceAll("$1");
        text = MARKDOWN_LINK.matcher(text).replaceAll("$1");
        text = HTML_LINE_BREAK.matcher(text).replaceAll("\n");
        text = HTML_BLOCK_TAG.matcher(text).replaceAll("\n\n");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = LINE_PREFIX.matcher(text).replaceAll("");
        text = EMPHASIS_MARK.matcher(text).replaceAll("");
        // 태그를 걷어낸 뒤에 엔티티를 푼다. 먼저 풀면 본문에 글자로 쓴 "&lt;b&gt;"가 태그로 오인돼 사라진다.
        text = decodeEntities(text);
        return splitParagraphs(text);
    }

    private static String lineBreaks(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<String> splitParagraphs(String text) {
        List<String> result = new ArrayList<>();
        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            String collapsed = collapse(paragraph);
            if (!collapsed.isEmpty()) {
                result.add(collapsed);
            }
        }
        return result;
    }

    /** 문단을 순서대로 이어 붙여 MAX_BODY_CHUNK_LENGTH 이하 구간을 만든다. 한 문단이 그보다 길면 그 문단만 따로 잘라 여러 구간으로 나눈다. */
    static List<String> packParagraphs(List<String> paragraphs) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > MAX_BODY_CHUNK_LENGTH) {
                flush(current, chunks);
                chunks.addAll(splitLongParagraph(paragraph));
                continue;
            }
            int nextLength =
                    current.isEmpty()
                            ? paragraph.length()
                            : current.length() + PARAGRAPH_SEPARATOR.length() + paragraph.length();
            if (nextLength > MAX_BODY_CHUNK_LENGTH) {
                flush(current, chunks);
            }
            if (!current.isEmpty()) {
                current.append(PARAGRAPH_SEPARATOR);
            }
            current.append(paragraph);
        }
        flush(current, chunks);
        return chunks;
    }

    /**
     * 긴 문단을 최대 MAX_BODY_CHUNK_LENGTH 길이로 자른다. 뒤쪽 절반 안에 공백이 있으면 단어가 끊기지 않게 그 공백에서 자르고, 없으면 그대로 자른다.
     * 서로게이트 쌍(이모지 등)은 둘로 나누지 않는다.
     */
    static List<String> splitLongParagraph(String paragraph) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (paragraph.length() - start > MAX_BODY_CHUNK_LENGTH) {
            int end = start + MAX_BODY_CHUNK_LENGTH;
            int space = paragraph.lastIndexOf(' ', end);
            if (space > start + MAX_BODY_CHUNK_LENGTH / 2) {
                end = space;
            } else if (Character.isHighSurrogate(paragraph.charAt(end - 1))) {
                end--;
            }
            pieces.add(paragraph.substring(start, end).strip());
            start = end;
            while (start < paragraph.length() && paragraph.charAt(start) == ' ') {
                start++;
            }
        }
        if (start < paragraph.length()) {
            pieces.add(paragraph.substring(start).strip());
        }
        return pieces;
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static SearchChunk chunk(int chunkNo, String text) {
        return new SearchChunk(chunkNo, text, sha256Hex(text));
    }

    private static void flush(StringBuilder current, List<String> chunks) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    /** 한글 자모 분리형(NFD)으로 들어온 입력도 같은 해시를 갖도록 NFC로 맞춘다. */
    private static String nfc(String raw) {
        return raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC);
    }

    private static String decodeEntities(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    private static String collapse(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
