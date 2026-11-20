package com.ikae.snowthing.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnonymousNicknameTest {

    @Test
    @DisplayName("같은 IP는 언제나 같은 닉네임을 받는다")
    void sameIpSameNickname() {
        assertThat(AnonymousNickname.of("203.0.113.7"))
                .isEqualTo(AnonymousNickname.of("203.0.113.7"));
    }

    @Test
    @DisplayName("닉네임은 형용사, 명사, #네 자리 번호로 이루어진다. IP가 없어도 만든다")
    void formatIsAdjectiveNounNumber() {
        for (String ip : List.of("203.0.113.7", "2001:db8::1", "")) {
            String nickname = AnonymousNickname.of(ip);
            assertThat(nickname).matches("\\S+#\\d{4}");
            assertThat(Arrays.stream(AnonymousNickname.ADJECTIVES).anyMatch(nickname::startsWith))
                    .isTrue();
            String body = nickname.substring(0, nickname.indexOf('#'));
            assertThat(Arrays.stream(AnonymousNickname.NOUNS).anyMatch(body::endsWith)).isTrue();
        }
        assertThat(AnonymousNickname.of(null)).matches("\\S+#\\d{4}");
    }

    @Test
    @DisplayName("비슷한 IP 1,000개가 다양한 이름으로 흩어지고, 이름과 번호가 서로 엮이지 않는다")
    void nicknamesAreDiverse() {
        List<String> nicknames =
                IntStream.range(0, 1_000)
                        .mapToObj(i -> AnonymousNickname.of("10.0." + (i / 256) + "." + (i % 256)))
                        .toList();
        Set<String> names = new HashSet<>();
        Set<String> adjectives = new HashSet<>();
        for (String nickname : nicknames) {
            String name = nickname.substring(0, nickname.indexOf('#'));
            names.add(name);
            Arrays.stream(AnonymousNickname.ADJECTIVES)
                    .filter(name::startsWith)
                    .forEach(adjectives::add);
        }
        assertThat(adjectives).hasSize(AnonymousNickname.ADJECTIVES.length);
        assertThat(names.size()).isGreaterThan(500);
        // 예전에는 이름이 번호 끝자리로 정해졌다. 같은 이름 안에서도 번호 끝자리가 여러 가지여야 한다.
        String first = nicknames.get(0).substring(0, nicknames.get(0).indexOf('#'));
        Set<Character> lastDigits = new HashSet<>();
        IntStream.range(0, 200_000)
                .mapToObj(i -> AnonymousNickname.of("172.16." + (i / 256) + "." + (i % 256)))
                .filter(nickname -> nickname.startsWith(first + "#"))
                .forEach(nickname -> lastDigits.add(nickname.charAt(nickname.length() - 1)));
        assertThat(lastDigits.size()).isGreaterThan(5);
    }

    @Test
    @DisplayName("운영진처럼 보이는 말은 쓰지 않는다")
    void noStaffLikeWords() {
        Set<String> words = new HashSet<>(Arrays.asList(AnonymousNickname.NOUNS));
        words.addAll(Arrays.asList(AnonymousNickname.ADJECTIVES));
        assertThat(words)
                .noneMatch(
                        word -> word.contains("운영") || word.contains("관리") || word.contains("패트롤"));
    }
}
