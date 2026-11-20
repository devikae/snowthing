package com.ikae.snowthing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.member.validation.NicknamePolicy;

class SocialNicknameGeneratorTest {

    private final SocialNicknameGenerator generator = new SocialNicknameGenerator();

    @Test
    @DisplayName("가장 긴 단어끼리 붙어도 닉네임 규칙(2~10자 한글, 영문, 숫자) 안이다")
    void longestCombinationFitsPolicy() {
        int longestAdjective =
                Arrays.stream(SocialNicknameGenerator.ADJECTIVES)
                        .mapToInt(String::length)
                        .max()
                        .orElseThrow();
        int longestNoun =
                Arrays.stream(SocialNicknameGenerator.NOUNS)
                        .mapToInt(String::length)
                        .max()
                        .orElseThrow();
        assertThat(longestAdjective + longestNoun + 2)
                .isLessThanOrEqualTo(NicknamePolicy.MAX_LENGTH);
        IntStream.range(0, 1_000)
                .forEach(i -> assertThat(generator.generate()).matches(NicknamePolicy.REGEXP));
    }

    @Test
    @DisplayName("운영진처럼 보이는 말은 쓰지 않는다")
    void noReservedWords() {
        IntStream.range(0, 1_000)
                .forEach(
                        i -> assertThat(NicknamePolicy.isReserved(generator.generate())).isFalse());
    }

    @Test
    @DisplayName("여러 번 만들면 서로 다른 닉네임이 고르게 나온다")
    void nicknamesAreDiverse() {
        Set<String> nicknames = new HashSet<>();
        IntStream.range(0, 200).forEach(i -> nicknames.add(generator.generate()));
        assertThat(nicknames.size()).isGreaterThan(190);
    }
}
