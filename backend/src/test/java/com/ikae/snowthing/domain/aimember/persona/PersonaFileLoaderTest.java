package com.ikae.snowthing.domain.aimember.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersonaFileLoaderTest {

    @Test
    @DisplayName("저장소의 예시 파일을 그대로 읽을 수 있다")
    void loadsExampleFile() {
        List<Persona> personas = PersonaFileLoader.load(Path.of("ai-personas.example.yml"));

        assertThat(personas).hasSize(1);
        assertThat(personas.get(0).email()).endsWith(PersonaFileLoader.EMAIL_DOMAIN);
    }

    @Test
    @DisplayName("YAML 파일에서 페르소나를 읽고 이메일은 소문자로 맞춘다")
    void loadsFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("personas.yml");
        Files.writeString(
                file,
                """
                personas:
                  - key: a1
                    email: AI-Agent01@snowthing.invalid
                    nickname: 테스트닉
                    ip: 211.36.10.20
                    description: |
                      카빙 좋아함
                """);

        List<Persona> personas = PersonaFileLoader.load(file);

        assertThat(personas)
                .containsExactly(
                        new Persona(
                                "a1",
                                "ai-agent01@snowthing.invalid",
                                "테스트닉",
                                "211.36.10.20",
                                "카빙 좋아함"));
    }

    @Test
    @DisplayName(".invalid가 아닌 이메일은 실제 메일이 나갈 수 있어 거부한다")
    void rejectsRealEmailDomain() {
        assertThatThrownBy(
                        () ->
                                PersonaFileLoader.parse(
                                        root(entry("a1", "a@gmail.com", "211.36.1.1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("사설·루프백 IP는 화면에서 티가 나므로 거부한다")
    void rejectsPrivateIp() {
        for (String ip :
                List.of("127.0.0.1", "10.0.0.5", "192.168.0.1", "172.16.0.1", "999.1.1.1")) {
            assertThatThrownBy(
                            () ->
                                    PersonaFileLoader.parse(
                                            root(entry("a1", "a@snowthing.invalid", ip))))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("키·이메일·닉네임이 겹치거나 5명 이상이면 거부한다")
    void rejectsDuplicatesAndTooMany() {
        Map<String, Object> first = entry("a1", "a1@snowthing.invalid", "211.36.1.1");
        Map<String, Object> sameKey = entry("a1", "a2@snowthing.invalid", "211.36.1.2");

        assertThatThrownBy(() -> PersonaFileLoader.parse(root(first, sameKey)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                PersonaFileLoader.parse(
                                        root(
                                                entry("a1", "a1@snowthing.invalid", "211.36.1.1"),
                                                entry("a2", "a2@snowthing.invalid", "211.36.1.2"),
                                                entry("a3", "a3@snowthing.invalid", "211.36.1.3"),
                                                entry("a4", "a4@snowthing.invalid", "211.36.1.4"),
                                                entry("a5", "a5@snowthing.invalid", "211.36.1.5"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @SafeVarargs
    private static Map<String, Object> root(Map<String, Object>... entries) {
        return Map.of("personas", List.of(entries));
    }

    private static Map<String, Object> entry(String key, String email, String ip) {
        return Map.of(
                "key", key, "email", email, "nickname", "닉" + key, "ip", ip, "description", "설명");
    }
}
