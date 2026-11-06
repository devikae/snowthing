package com.ikae.snowthing.domain.charactertest.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CharacterAnswersTest {

    @Test
    void 가능한_답변_4096가지가_모두_동점_없이_16유형_중_하나로_판정된다() {
        Set<CharacterType> reached = EnumSet.noneOf(CharacterType.class);
        for (int bits = 0; bits < 4096; bits++) {
            StringBuilder value = new StringBuilder(12);
            for (int index = 0; index < 12; index++) {
                value.append((bits >> index & 1) == 0 ? 'A' : 'B');
            }
            reached.add(new CharacterAnswers(value + "CBA").type());
        }
        assertThat(reached).containsExactlyInAnyOrder(CharacterType.values());
    }

    @ParameterizedTest
    @CsvSource({
        "AAAAAAAAAAAAAAA,ESTJ,CAPTAIN,PLANNED",
        "AAAAAAAAABBBAAA,ESTP,CAPTAIN,SPONTANEOUS",
        "AAAAAABBBAAAAAA,ESFJ,RESORT_INSIDER,PLANNED",
        "AAAAAABBBBBBAAA,ESFP,RESORT_INSIDER,SPONTANEOUS",
        "AAABBBAAAAAAAAA,ENTJ,HYPE,PLANNED",
        "AAABBBAAABBBAAA,ENTP,HYPE,SPONTANEOUS",
        "AAABBBBBBAAAAAA,ENFJ,MOOD_MAKER,PLANNED",
        "AAABBBBBBBBBAAA,ENFP,MOOD_MAKER,SPONTANEOUS",
        "BBBAAAAAAAAAAAA,ISTJ,GRINDER,PLANNED",
        "BBBAAAAAABBBAAA,ISTP,GRINDER,SPONTANEOUS",
        "BBBAAABBBAAAAAA,ISFJ,HOT_PACK,PLANNED",
        "BBBAAABBBBBBAAA,ISFP,HOT_PACK,SPONTANEOUS",
        "BBBBBBAAAAAAAAA,INTJ,GEAR_GEEK,PLANNED",
        "BBBBBBAAABBBAAA,INTP,GEAR_GEEK,SPONTANEOUS",
        "BBBBBBBBBAAAAAA,INFJ,RESORT_OUTSIDER,PLANNED",
        "BBBBBBBBBBBBAAA,INFP,RESORT_OUTSIDER,SPONTANEOUS"
    })
    void 유형과_직업과_계획형_구분이_설계_표와_같다(
            String answers, CharacterType type, CharacterJob job, PlanStyle planStyle) {
        CharacterType actual = new CharacterAnswers(answers).type();
        assertThat(actual).isEqualTo(type);
        assertThat(actual.job()).isEqualTo(job);
        assertThat(actual.planStyle()).isEqualTo(planStyle);
    }

    @Test
    void 축마다_셋_중_둘이면_다수결로_정한다() {
        assertThat(new CharacterAnswers("ABABABABABABAAA").type()).isEqualTo(CharacterType.ENTP);
        assertThat(new CharacterAnswers("AABBBAAABBBAAAA").type()).isEqualTo(CharacterType.ENTP);
        assertThat(new CharacterAnswers("BBAAABBBAAABAAA").type()).isEqualTo(CharacterType.ISFJ);
    }

    @Test
    void 스탯은_각_축에서_해당_쪽을_고른_답의_수다() {
        // 사교 A 2개, 모험 B 1개, 다정 B 3개, 계획 A 0개
        CharacterStats stats = new CharacterAnswers("AABAABBBBBBBAAA").stats();
        assertThat(stats).isEqualTo(new CharacterStats(2, 1, 3, 0));
    }

    @Test
    void 근거_질문은_축마다_다수_쪽을_고른_첫_질문이다() {
        assertThat(new CharacterAnswers("BAABBABAAABBAAA").highlightQuestions())
                .isEqualTo(List.of(2, 4, 8, 11));
    }

    @Test
    void 일치_수는_같은_자리의_답이_같은_개수다() {
        CharacterAnswers left = new CharacterAnswers("AAAAAAAAAAAAAAA");
        assertThat(left.matchCount(left)).isEqualTo(12);
        assertThat(left.matchCount(new CharacterAnswers("BBBBBBBBBBBBAAA"))).isZero();
        assertThat(left.matchCount(new CharacterAnswers("AAABBBAAABBBAAA"))).isEqualTo(6);
    }

    @Test
    void 습관_차이는_질문별_척도_거리이고_유형에는_영향이_없다() {
        CharacterAnswers left = new CharacterAnswers("AAAAAAAAAAAAABC");
        CharacterAnswers right = new CharacterAnswers("AAAAAAAAAAAACBC");
        assertThat(left.habitGaps(right)).containsExactly(2, 0, 0);
        assertThat(left.type()).isEqualTo(right.type());
        assertThat(left.matchCount(right)).isEqualTo(12);
    }

    @Test
    void 궁합_점수는_표와_습관과_일치를_더하고_단계는_점수로_정한다() {
        CharacterAnswers mine = new CharacterAnswers("AAAAAAAAAAAAAAA");
        CharacterCompatibility top = CharacterCompatibility.of(CompatibilityLevel.BEST, mine, mine);
        assertThat(top.score()).isEqualTo(100);
        assertThat(top.level()).isEqualTo(CompatibilityLevel.BEST);

        CharacterCompatibility bottom =
                CharacterCompatibility.of(
                        CompatibilityLevel.APART, mine, new CharacterAnswers("BBBBBBBBBBBBCCC"));
        assertThat(bottom.score()).isEqualTo(16);
        assertThat(bottom.level()).isEqualTo(CompatibilityLevel.APART);

        // 표에서는 무난이어도 습관과 답변이 다 맞으면 찰떡까지 올라간다.
        CharacterCompatibility raised =
                CharacterCompatibility.of(CompatibilityLevel.NEUTRAL, mine, mine);
        assertThat(raised.score()).isEqualTo(80);
        assertThat(raised.level()).isEqualTo(CompatibilityLevel.BEST);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "AAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAA",
                "CAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAD",
                "aaaaaaaaaaaaaaa"
            })
    void 형식이_틀린_답변은_거절한다(String value) {
        assertThatThrownBy(() -> new CharacterAnswers(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
