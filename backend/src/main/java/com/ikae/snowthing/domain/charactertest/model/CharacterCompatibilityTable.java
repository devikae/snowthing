package com.ikae.snowthing.domain.charactertest.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 궁합표 {@code character-test/compatibility.csv}를 읽어 둔다. 한 줄이 한 조합이라 대칭은 형식으로 보장되고, 빠지거나 중복된 조합이 있으면
 * 기동을 멈춘다. 사용자가 표를 고치다 실수해도 틀린 표로 서비스되지 않게 하려는 것이다.
 */
@Component
public class CharacterCompatibilityTable {

    public static final String RESOURCE = "character-test/compatibility.csv";
    public static final int PAIR_COUNT = 136;

    private final Map<CharacterType, Map<CharacterType, CompatibilityLevel>> levels;

    @Autowired
    public CharacterCompatibilityTable() {
        this(openResource());
    }

    public CharacterCompatibilityTable(InputStream source) {
        this.levels = parse(source);
    }

    public CompatibilityLevel levelOf(CharacterType left, CharacterType right) {
        return levels.get(left).get(right);
    }

    /** 찰떡 상대가 여럿이면 유형 선언 순서의 첫 상대를 고른다. 같은 유형은 언제나 같은 결과가 나온다. */
    public Optional<CharacterType> bestMatchOf(CharacterType type) {
        for (CharacterType candidate : CharacterType.values()) {
            if (levelOf(type, candidate) == CompatibilityLevel.BEST) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static InputStream openResource() {
        try {
            return new ClassPathResource(RESOURCE).getInputStream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Map<CharacterType, Map<CharacterType, CompatibilityLevel>> parse(
            InputStream source) {
        Map<CharacterType, Map<CharacterType, CompatibilityLevel>> table =
                new EnumMap<>(CharacterType.class);
        for (CharacterType type : CharacterType.values()) {
            table.put(type, new EnumMap<>(CharacterType.class));
        }
        int pairs = 0;
        for (String line : readLines(source)) {
            String[] parts = line.split(",");
            if (parts.length != 3) {
                throw new IllegalStateException("궁합표 형식 오류: " + line);
            }
            CharacterType left = CharacterType.valueOf(parts[0].trim());
            CharacterType right = CharacterType.valueOf(parts[1].trim());
            CompatibilityLevel level =
                    CompatibilityLevel.ofLabel(parts[2].trim())
                            .orElseThrow(() -> new IllegalStateException("궁합표 단계 이름 오류: " + line));
            if (table.get(left).containsKey(right)) {
                throw new IllegalStateException("궁합표 조합 중복: " + line);
            }
            table.get(left).put(right, level);
            table.get(right).put(left, level);
            pairs++;
        }
        if (pairs != PAIR_COUNT) {
            throw new IllegalStateException("궁합표 조합 수 오류: " + pairs);
        }
        return table;
    }

    private static List<String> readLines(InputStream source) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return lines;
    }
}
