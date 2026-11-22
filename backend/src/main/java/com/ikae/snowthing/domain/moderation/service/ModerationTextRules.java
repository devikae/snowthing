package com.ikae.snowthing.domain.moderation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ikae.snowthing.domain.aimember.PoliticalContentFilter;
import com.ikae.snowthing.domain.contentfilter.service.BannedWordMatcher;
import com.ikae.snowthing.domain.moderation.entity.ModerationTargetType;

/**
 * 1단계 규칙 중 글 내용만 보는 부분 (IKA-80). 상태가 없어 단위 테스트로 패턴을 하나씩 확인한다.
 *
 * <p>금칙어(도박, 광고 말)는 IKA-96 목록을 그대로 쓰므로 여기에 두지 않는다. 여기서는 IKA-96이 보지 않는 외부 연락 유도, 외부 링크 수, 정치 글을 본다.
 *
 * <ul>
 *   <li>텔레그램: t.me 링크, "텔레그램", "텔레 아이디", "텔레:" 표기. "텔레비전"은 걸리지 않는다.
 *   <li>오픈채팅: open.kakao.com 링크. "오픈채팅방 만들었어요" 같은 말만으로는 걸리지 않는다.
 *   <li>카카오톡 ID: "카톡 아이디 abc123", "카톡: abc123", "ㅋㅌ ID abc" 처럼 카톡 표기 뒤에 아이디가 오는 경우.
 *   <li>짧은 URL: bit.ly 같은 단축 주소. 지도 공유에 흔한 naver.me는 넣지 않는다.
 * </ul>
 *
 * <p>중고장터와 카풀은 거래와 동승 연락이 본래 목적이라 오픈채팅 링크와 카톡 아이디 표기를 보지 않는다. 텔레그램과 단축 주소는 똑같이 본다.
 */
public final class ModerationTextRules {

    public static final String CONTACT_TELEGRAM = "CONTACT_TELEGRAM";
    public static final String CONTACT_OPEN_CHAT = "CONTACT_OPEN_CHAT";
    public static final String CONTACT_KAKAO_ID = "CONTACT_KAKAO_ID";
    public static final String CONTACT_SHORT_URL = "CONTACT_SHORT_URL";

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z]+);");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}]+");

    private static final Pattern TELEGRAM =
            Pattern.compile(
                    "(?<![a-z0-9.])(?:t|telegram)\\.me/[a-z0-9_+]"
                            + "|telegram"
                            + "|텔레그램"
                            + "|텔레\\s*(?:아이디|아뒤|id\\b|[:：@])");
    private static final Pattern OPEN_CHAT = Pattern.compile("open\\.kakao\\.com/");
    // 글을 NFKC로 정리하면 "ㅋㅌ" 같은 호환 자모가 다른 코드로 바뀌므로 패턴도 같은 방식으로 정리한다.
    private static final Pattern KAKAO_ID =
            Pattern.compile(
                    Normalizer.normalize(
                            "(?:카톡|카카오톡|카카오|ㅋㅌ|kakaotalk|katalk|kakao)\\s*"
                                    + "(?:(?:아이디|아뒤|id)\\s*[:：]?|[:：])\\s*"
                                    + "@?[a-z0-9][a-z0-9_.-]{2,}",
                            Normalizer.Form.NFKC));
    private static final Pattern SHORT_URL =
            Pattern.compile(
                    "(?<![a-z0-9.-])(?:bit\\.ly|abit\\.ly|tinyurl\\.com|goo\\.gl|t\\.co|is\\.gd"
                            + "|han\\.gl|me2\\.do|vo\\.la|url\\.kr|buly\\.kr|c11\\.kr|shorturl\\.at"
                            + "|cutt\\.ly|rebrand\\.ly|ow\\.ly|tiny\\.cc|lrl\\.kr|zxcv\\.be)/[a-z0-9]");
    private static final Pattern URL = Pattern.compile("(?:https?://|www\\.)[^\\s<>\"'()\\[\\]]+");
    // 포트는 숫자만 허용하고 바로 뒤에서 주소의 호스트 부분이 끝나야 한다. "snowthing.org:x@다른곳"은 @ 뒤가 실제 호스트다.
    private static final Pattern OWN_HOST =
            Pattern.compile(
                    "^(?:https?://)?(?:[a-z0-9-]+\\.)*snowthing\\.org(?::[0-9]+)?(?:[/?#]|$)");

    private ModerationTextRules() {}

    /** 제목과 본문에서 찾은 것. */
    public static Findings inspect(ModerationTargetType type, String title, String body) {
        String raw = fold(join(title, body));
        String plain = fold(plainText(join(title, body)));
        // 링크는 HTML 속성(href) 안에도 있으므로 태그를 지우기 전 글도 함께 본다.
        String scan = raw + "\n" + plain;
        Set<String> contacts = new LinkedHashSet<>();
        if (TELEGRAM.matcher(scan).find()) contacts.add(CONTACT_TELEGRAM);
        boolean tradeContactAllowed =
                type == ModerationTargetType.MARKET || type == ModerationTargetType.CARPOOL;
        if (!tradeContactAllowed && OPEN_CHAT.matcher(scan).find()) {
            contacts.add(CONTACT_OPEN_CHAT);
        }
        if (!tradeContactAllowed && KAKAO_ID.matcher(plain).find()) {
            contacts.add(CONTACT_KAKAO_ID);
        }
        if (SHORT_URL.matcher(scan).find()) contacts.add(CONTACT_SHORT_URL);
        return new Findings(
                List.copyOf(contacts),
                // 원문과 정리한 글을 합쳐 세면 엔티티나 태그 때문에 모양이 달라진 같은 주소를 두 번 센다. 따로 세어 큰 쪽을 쓴다.
                Math.max(externalLinkCount(raw), externalLinkCount(plain)),
                PoliticalContentFilter.containsPolitics(plainText(join(title, body))),
                BannedWordMatcher.normalize(title).length()
                        + BannedWordMatcher.normalize(body).length());
    }

    /** 다른 주소의 수. 같은 주소를 여러 번 적어도 하나로 센다. 눈팅 주소(사진 포함)는 세지 않는다. */
    static int externalLinkCount(String text) {
        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String link = matcher.group();
            if (!OWN_HOST.matcher(link).find()) {
                links.add(link.replaceFirst("^https?://", "").replaceFirst("^www\\.", ""));
            }
        }
        return links.size();
    }

    /**
     * 제목과 본문을 정리한 뒤 SHA-256. 정리는 금칙어 비교와 같다(태그 제거, NFKC, 소문자, 글자와 숫자만). 기호나 띄어쓰기만 바꾼 복사본도 같은 값이 된다.
     */
    public static String contentHash(String title, String body) {
        String normalized =
                BannedWordMatcher.normalize(title) + "\u0000" + BannedWordMatcher.normalize(body);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 2단계로 보내는 글. 태그를 지운 제목과 본문뿐이고 길이를 자른다. */
    public static String apiInput(String title, String body, int maxLength) {
        String text = plainText(join(title, body)).replaceAll("\\s+", " ").strip();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    static String plainText(String text) {
        if (text == null || text.isEmpty()) return "";
        return ENTITY.matcher(TAG.matcher(text).replaceAll(" ")).replaceAll(" ");
    }

    private static String fold(String text) {
        return INVISIBLE
                .matcher(Normalizer.normalize(text, Normalizer.Form.NFKC))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }

    private static String join(String title, String body) {
        List<String> parts = new ArrayList<>(2);
        if (title != null && !title.isBlank()) parts.add(title);
        if (body != null && !body.isBlank()) parts.add(body);
        return String.join("\n", parts);
    }

    /**
     * @param contacts 걸린 외부 연락 유도 종류 (CONTACT_*)
     * @param externalLinkCount 눈팅 밖 주소 수
     * @param political 정치 글 (이것만으로는 숨기지 않고 검토 대기)
     * @param normalizedLength 정리한 제목과 본문 글자 수
     */
    public record Findings(
            List<String> contacts,
            int externalLinkCount,
            boolean political,
            int normalizedLength) {}
}
