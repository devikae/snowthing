package com.ikae.snowthing.domain.aimember.persona;

import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.ikae.snowthing.domain.aimember.PoliticalContentFilter;
import com.ikae.snowthing.domain.aimember.persona.Persona.Activity;
import com.ikae.snowthing.domain.aimember.persona.Persona.Window;

/**
 * 저장소 밖 페르소나 YAML을 읽는다. 형식은 {@code backend/ai-personas.example.yml}.
 *
 * <p>잘못된 파일로 엉뚱한 계정이 만들어지지 않도록 하나라도 틀리면 전체를 거부한다.
 */
public final class PersonaFileLoader {

    public static final String EMAIL_DOMAIN = "@snowthing.invalid";
    private static final int MAX_PERSONAS = 12;
    private static final Set<String> BOARDS = Set.of("FREE", "ANONYMOUS", "QNA");
    private static final Pattern WINDOW = Pattern.compile("^(\\d{1,2})-(\\d{1,2})$");
    private static final Pattern KEY = Pattern.compile("^[a-z0-9-]{1,20}$");
    private static final Pattern IPV4 =
            Pattern.compile(
                    "^(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)$");

    private PersonaFileLoader() {}

    public static List<Persona> load(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            return parse(root);
        } catch (IOException exception) {
            throw new IllegalStateException("AI member persona file is not readable", exception);
        }
    }

    static List<Persona> parse(Object root) {
        if (!(root instanceof Map<?, ?> map) || !(map.get("personas") instanceof List<?> items)) {
            throw invalid("personas list is missing");
        }
        if (items.isEmpty() || items.size() > MAX_PERSONAS) {
            throw invalid("personas must have 1 to " + MAX_PERSONAS + " entries");
        }
        List<Persona> personas = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> emails = new HashSet<>();
        Set<String> nicknames = new HashSet<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> entry)) {
                throw invalid("persona entry must be a map");
            }
            Persona persona =
                    new Persona(
                            text(entry, "key"),
                            text(entry, "email").toLowerCase(),
                            text(entry, "nickname"),
                            text(entry, "ip"),
                            text(entry, "description"),
                            activity(entry));
            validate(persona);
            if (!keys.add(persona.key())
                    || !emails.add(persona.email())
                    || !nicknames.add(persona.nickname())) {
                throw invalid("key, email and nickname must be unique");
            }
            personas.add(persona);
        }
        return List.copyOf(personas);
    }

    /** 활동 항목은 모두 선택이다. 없으면 기본값을 쓴다. */
    private static Activity activity(Map<?, ?> entry) {
        Activity fallback = Activity.DEFAULT;
        return new Activity(
                entry.containsKey("activeHours")
                        ? windows(text(entry, "activeHours"))
                        : fallback.windows(),
                entry.containsKey("postsPerDay") ? postsPerDay(entry) : fallback.postsPerDay(),
                entry.containsKey("boards") ? boards(entry.get("boards")) : fallback.boards(),
                entry.containsKey("topics") ? topics(entry.get("topics")) : fallback.topics());
    }

    /** "7-9,21-24"처럼 쓴다. 자정을 넘기는 시간대는 "22-24,0-2"로 나눈다. */
    private static List<Window> windows(String value) {
        List<Window> windows = new ArrayList<>();
        for (String part : value.split(",")) {
            Matcher matcher = WINDOW.matcher(part.strip());
            if (!matcher.matches()) {
                throw invalid("activeHours must look like 7-9,21-24");
            }
            int start = Integer.parseInt(matcher.group(1));
            int end = Integer.parseInt(matcher.group(2));
            if (start >= end || end > 24) {
                throw invalid("activeHours must be within 0 to 24");
            }
            windows.add(new Window(start, end));
        }
        return List.copyOf(windows);
    }

    private static double postsPerDay(Map<?, ?> entry) {
        if (!(entry.get("postsPerDay") instanceof Number number)
                || number.doubleValue() < 0
                || number.doubleValue() > 2.5) {
            throw invalid("postsPerDay must be between 0 and 2.5");
        }
        return number.doubleValue();
    }

    private static Map<String, Integer> boards(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            throw invalid("boards must be a map");
        }
        Map<String, Integer> boards = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!BOARDS.contains(entry.getKey())
                    || !(entry.getValue() instanceof Integer weight)
                    || weight <= 0) {
                throw invalid("boards must map FREE, ANONYMOUS, QNA to positive integers");
            }
            boards.put((String) entry.getKey(), weight);
        }
        return Map.copyOf(boards);
    }

    /** 정치 글감이 파일에 섞이면 기동 때 바로 알 수 있게 파일 전체를 거부한다. */
    private static List<String> topics(Object value) {
        if (!(value instanceof List<?> items)) {
            throw invalid("topics must be a list");
        }
        List<String> topics = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof String topic) || topic.isBlank() || topic.length() > 100) {
                throw invalid("topic must be text of at most 100 characters");
            }
            if (PoliticalContentFilter.containsPolitics(topic)) {
                throw invalid("topic must not be political");
            }
            topics.add(topic.strip());
        }
        return List.copyOf(topics);
    }

    private static void validate(Persona persona) {
        if (!KEY.matcher(persona.key()).matches()) {
            throw invalid("key must match " + KEY.pattern());
        }
        if (!persona.email().endsWith(EMAIL_DOMAIN) || persona.email().length() > 100) {
            throw invalid("email must end with " + EMAIL_DOMAIN);
        }
        if (persona.nickname().length() < 2 || persona.nickname().length() > 50) {
            throw invalid("nickname must be 2 to 50 characters");
        }
        if (persona.description().length() > 2_000) {
            throw invalid("description must be at most 2000 characters");
        }
        if (!isPublicIpv4(persona.writerIp())) {
            throw invalid("ip must be a public IPv4 address");
        }
    }

    private static boolean isPublicIpv4(String value) {
        if (!IPV4.matcher(value).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return !(address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()
                    || address.isMulticastAddress());
        } catch (IOException exception) {
            return false;
        }
    }

    private static String text(Map<?, ?> entry, String name) {
        if (!(entry.get(name) instanceof String value) || value.isBlank()) {
            throw invalid(name + " is required");
        }
        return value.strip();
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Invalid AI member persona file: " + reason);
    }
}
