package com.ikae.snowthing.domain.aimember;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 글자 3-gram 자카드 유사도. 조사와 어미만 바꾼 한국어 문장도 겹치는 글자가 많아 잡힌다. */
final class AiTextSimilarity {

    static final double TITLE_THRESHOLD = 0.6;
    static final double BODY_THRESHOLD = 0.5;
    private static final Pattern NOT_LETTER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int GRAM = 3;

    private AiTextSimilarity() {}

    static boolean tooSimilar(String text, Collection<String> references, double threshold) {
        String normalized = normalize(text);
        Set<String> grams = grams(normalized);
        return references.stream()
                .map(AiTextSimilarity::normalize)
                .anyMatch(
                        other ->
                                other.equals(normalized)
                                        || jaccard(grams, grams(other)) >= threshold);
    }

    static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> common = new HashSet<>(left);
        common.retainAll(right);
        return (double) common.size() / (left.size() + right.size() - common.size());
    }

    private static String normalize(String text) {
        return NOT_LETTER.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static Set<String> grams(String text) {
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + GRAM <= text.length(); i++) {
            grams.add(text.substring(i, i + GRAM));
        }
        return grams;
    }
}
