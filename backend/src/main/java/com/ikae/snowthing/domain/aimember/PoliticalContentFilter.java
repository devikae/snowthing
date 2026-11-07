package com.ikae.snowthing.domain.aimember;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * AI 멤버가 정치 얘기를 하거나 받아주지 않게 금칙어로 거른다. 프롬프트만 믿지 않기 위한 마지막 관문이다.
 *
 * <p>잘못 걸려도 글 하나를 버릴 뿐이고 놓치면 사이트 신뢰가 깨지므로 넓게 잡는다. 금칙어는 {@code ai/political-terms.txt}에 있다.
 */
public final class PoliticalContentFilter {

    private static final String RESOURCE = "/ai/political-terms.txt";
    private static final Pattern NOT_LETTER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}]+");
    private static final String AFTER_WORD =
            "(?=$|[^가-힣a-z0-9]|은|는|이|가|을|를|의|에|도|만|로|으로|와|과|랑|들|놈|년|충|하|적|철|때|쪽|측|권|질|인)";

    private static final List<String> ANY = new ArrayList<>();
    private static final List<Pattern> PATTERNS = new ArrayList<>();

    static {
        load();
    }

    private PoliticalContentFilter() {}

    public static boolean containsPolitics(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized =
                INVISIBLE
                        .matcher(Normalizer.normalize(text, Normalizer.Form.NFKC))
                        .replaceAll("")
                        .toLowerCase(Locale.ROOT);
        String compact = NOT_LETTER.matcher(normalized).replaceAll("");
        return ANY.stream().anyMatch(compact::contains)
                || PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    private static void load() {
        try (InputStream stream = PoliticalContentFilter.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing resource: " + RESOURCE);
            }
            String section = "";
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String raw = reader.readLine(); raw != null; raw = reader.readLine()) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]") && line.length() <= 7) {
                    section = line;
                    continue;
                }
                switch (section) {
                    case "[any]" -> ANY.add(line.toLowerCase(Locale.ROOT));
                    case "[word]" ->
                            PATTERNS.add(
                                    Pattern.compile(
                                            "(?<![가-힣a-z0-9])"
                                                    + Pattern.quote(line.toLowerCase(Locale.ROOT))
                                                    + AFTER_WORD));
                    case "[regex]" -> PATTERNS.add(Pattern.compile(line));
                    default -> throw new IllegalStateException("Term outside a section: " + line);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
