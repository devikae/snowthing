package com.ikae.snowthing.domain.aimember.persona;

import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 저장소 밖 페르소나 YAML을 읽는다. 형식은 {@code backend/ai-personas.example.yml}.
 *
 * <p>잘못된 파일로 엉뚱한 계정이 만들어지지 않도록 하나라도 틀리면 전체를 거부한다.
 */
public final class PersonaFileLoader {

    public static final String EMAIL_DOMAIN = "@snowthing.invalid";
    private static final int MAX_PERSONAS = 4;
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
                            text(entry, "description"));
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
