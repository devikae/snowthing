package com.ikae.snowthing.domain.contentfilter.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 금칙어 비교 (IKA-96). 상태가 없는 값 객체라 목록이 바뀌면 새로 만든다.
 *
 * <p>비교 전에 글과 금칙어를 같은 방식으로 정리한다.
 *
 * <ol>
 *   <li>HTML 태그와 엔티티(&amp;nbsp; 등)를 지운다. 에디터 본문이 HTML이라 "카&lt;b&gt;지&lt;/b&gt;노"도 잡는다.
 *   <li>NFKC로 전각 문자와 호환 문자를 보통 글자로 바꾸고 소문자로 만든다.
 *   <li>글자와 숫자만 남긴다. 공백, 점, 하이픈, 이모지, 보이지 않는 문자(zero width space)를 끼워 넣은 "카 지 노", "카.지.노"가 "카지노"가
 *       된다.
 * </ol>
 *
 * <p>띄어쓰기를 무시하므로 짧은 금칙어는 다른 단어 사이에 걸칠 수 있다. 그래서 일상어와 겹치는 짧은 말(토토, 먹튀, 카지노 단독)은 기본 목록에 넣지 않고 광고에만 쓰는
 * 말을 넣는다. 정리한 금칙어가 2자보다 짧으면 등록을 막는다.
 */
public final class BannedWordMatcher {

    public static final int MIN_NORMALIZED_LENGTH = 2;

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z]+);");

    private final List<Entry> entries;

    public BannedWordMatcher(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static BannedWordMatcher empty() {
        return new BannedWordMatcher(List.of());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 처음 걸린 금칙어의 원래 표기. 여러 글(제목, 본문)을 차례로 본다. */
    public Optional<String> findMatch(String... texts) {
        if (entries.isEmpty() || texts == null) return Optional.empty();
        for (String text : texts) {
            String normalized = normalize(text);
            if (normalized.isEmpty()) continue;
            for (Entry entry : entries) {
                if (normalized.contains(entry.normalizedWord())) {
                    return Optional.of(entry.word());
                }
            }
        }
        return Optional.empty();
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        String withoutMarkup = ENTITY.matcher(TAG.matcher(text).replaceAll(" ")).replaceAll(" ");
        String folded =
                Normalizer.normalize(withoutMarkup, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder kept = new StringBuilder(folded.length());
        folded.codePoints().filter(Character::isLetterOrDigit).forEach(kept::appendCodePoint);
        return kept.toString();
    }

    public record Entry(String word, String normalizedWord) {}
}
