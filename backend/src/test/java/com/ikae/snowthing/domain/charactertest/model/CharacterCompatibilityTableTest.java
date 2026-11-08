package com.ikae.snowthing.domain.charactertest.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CharacterCompatibilityTableTest {

    private final CharacterCompatibilityTable table = new CharacterCompatibilityTable();

    @Test
    void 파일에_136개_조합이_한_번씩_들어_있다() throws IOException {
        List<String> rows = rows();
        assertThat(rows).hasSize(136);
        Set<Set<String>> pairs = new HashSet<>();
        for (String row : rows) {
            String[] parts = row.split(",");
            assertThat(pairs.add(new HashSet<>(List.of(parts[0], parts[1]))))
                    .as("조합 중복: %s", row)
                    .isTrue();
        }
        assertThat(pairs).hasSize(136);
    }

    @Test
    void 표는_대칭이고_모든_칸이_채워져_있다() {
        for (CharacterType left : CharacterType.values()) {
            for (CharacterType right : CharacterType.values()) {
                assertThat(table.levelOf(left, right)).isNotNull();
                assertThat(table.levelOf(left, right)).isEqualTo(table.levelOf(right, left));
            }
        }
    }

    @Test
    void 다섯_단계가_모두_쓰인다() {
        Set<CompatibilityLevel> used = EnumSet.noneOf(CompatibilityLevel.class);
        for (CharacterType left : CharacterType.values()) {
            for (CharacterType right : CharacterType.values()) {
                used.add(table.levelOf(left, right));
            }
        }
        assertThat(used).containsExactlyInAnyOrder(CompatibilityLevel.values());
    }

    @Test
    void 모든_유형이_위의_두_단계에_한_명_이상_있다() {
        for (CharacterType type : CharacterType.values()) {
            Set<CompatibilityLevel> levels = EnumSet.noneOf(CompatibilityLevel.class);
            for (CharacterType other : CharacterType.values()) {
                levels.add(table.levelOf(type, other));
            }
            assertThat(levels)
                    .as(type.name())
                    .contains(CompatibilityLevel.BEST, CompatibilityLevel.GOOD);
            assertThat(table.bestMatchOf(type)).isPresent();
        }
    }

    @Test
    void 지시받은_찰떡_조합이_표에_있다() {
        assertBest(List.of("ENFP", "ENTP"), List.of("INFJ", "INTJ"));
        assertBest(List.of("INFP"), List.of("ENFJ", "ENTJ"));
        assertBest(List.of("INTP"), List.of("ENTJ", "ESTJ"));
        assertBest(List.of("ISFP"), List.of("ENFJ", "ESFJ", "ESTJ"));
        assertBest(List.of("ISTP"), List.of("ESFJ", "ESTJ"));
        assertBest(List.of("ESFP", "ESTP"), List.of("ISFJ", "ISTJ"));
    }

    @Test
    void 조합이_빠지면_읽지_않는다() throws IOException {
        List<String> rows = rows();
        rows.remove(0);
        assertThatThrownBy(() -> load(rows))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("조합 수");
    }

    @Test
    void 순서를_바꿔_같은_조합을_또_적으면_읽지_않는다() throws IOException {
        List<String> rows = rows();
        String first =
                rows.stream().filter(row -> !row.startsWith("ESTJ,ESTJ")).findFirst().orElseThrow();
        String[] parts = first.split(",");
        rows.add(parts[1] + "," + parts[0] + "," + parts[2]);
        assertThatThrownBy(() -> load(rows))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("중복");
    }

    @Test
    void 없는_단계_이름은_읽지_않는다() throws IOException {
        List<String> rows = rows();
        rows.set(0, rows.get(0).split(",")[0] + "," + rows.get(0).split(",")[1] + ",최고");
        assertThatThrownBy(() -> load(rows))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("단계 이름");
    }

    private void assertBest(List<String> lefts, List<String> rights) {
        for (String left : lefts) {
            for (String right : rights) {
                assertThat(table.levelOf(CharacterType.valueOf(left), CharacterType.valueOf(right)))
                        .as(left + "," + right)
                        .isEqualTo(CompatibilityLevel.BEST);
            }
        }
    }

    private static void load(List<String> rows) {
        new CharacterCompatibilityTable(
                new ByteArrayInputStream(String.join("\n", rows).getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> rows() throws IOException {
        try (InputStream source =
                new ClassPathResource(CharacterCompatibilityTable.RESOURCE).getInputStream()) {
            List<String> rows = new ArrayList<>();
            for (String line :
                    new String(source.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    rows.add(trimmed);
                }
            }
            return rows;
        }
    }
}
