package com.ikae.snowthing.domain.aimember.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.aimember.persona.Persona.Activity;
import com.ikae.snowthing.domain.aimember.persona.Persona.Window;

class PersonaActivityLoaderTest {

    @Test
    @DisplayName("활동 항목을 읽고 없으면 기본값을 쓴다")
    void parsesActivity() {
        Map<String, Object> full = entry(1);
        full.put("activeHours", "22-24, 0-2");
        full.put("postsPerDay", 0.4);
        full.put("boards", Map.of("FREE", 3, "QNA", 1));
        full.put("topics", List.of("왁싱"));

        List<Persona> personas =
                PersonaFileLoader.parse(Map.of("personas", List.of(full, entry(2))));

        assertThat(personas.get(0).activity())
                .isEqualTo(
                        new Activity(
                                List.of(new Window(22, 24), new Window(0, 2)),
                                0.4,
                                Map.of("FREE", 3, "QNA", 1),
                                List.of("왁싱")));
        assertThat(personas.get(0).activity().activeAt(1)).isTrue();
        assertThat(personas.get(0).activity().activeAt(2)).isFalse();
        assertThat(personas.get(1).activity()).isEqualTo(Activity.DEFAULT);
    }

    @Test
    @DisplayName("30명까지 받고 31명이면 거부한다")
    void limitsPersonaCount() {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            entries.add(entry(i));
        }
        assertThat(PersonaFileLoader.parse(Map.of("personas", entries))).hasSize(30);

        entries.add(entry(31));
        assertThatThrownBy(() -> PersonaFileLoader.parse(Map.of("personas", entries)))
                .hasMessageContaining("personas");
    }

    @Test
    @DisplayName("정치 글감이 있으면 파일 전체를 거부한다")
    void rejectsPoliticalTopic() {
        Map<String, Object> entry = entry(1);
        entry.put("topics", List.of("왁싱", "이번 대선 결과"));

        assertThatThrownBy(() -> PersonaFileLoader.parse(Map.of("personas", List.of(entry))))
                .hasMessageContaining("political");
    }

    @Test
    @DisplayName("잘못된 활동 값은 거부한다")
    void rejectsInvalidActivity() {
        assertInvalid("activeHours", "9-7");
        assertInvalid("activeHours", "22-26");
        assertInvalid("activeHours", "아침");
        assertInvalid("postsPerDay", 3);
        assertInvalid("postsPerDay", -1);
        assertInvalid("boards", Map.of("MARKET", 1));
        assertInvalid("boards", Map.of("FREE", 0));
        assertInvalid("topics", "왁싱");
    }

    private static void assertInvalid(String key, Object value) {
        Map<String, Object> entry = entry(1);
        entry.put(key, value);
        assertThatThrownBy(() -> PersonaFileLoader.parse(Map.of("personas", List.of(entry))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Map<String, Object> entry(int number) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("key", "a" + number);
        entry.put("email", "ai-agent" + number + "@snowthing.invalid");
        entry.put("nickname", "닉네임" + number);
        entry.put("ip", "211.36.10." + number);
        entry.put("description", "설명");
        return entry;
    }
}
