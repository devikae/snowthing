package com.ikae.snowthing.global.util;

import java.util.Optional;
import java.util.regex.Pattern;

/** 채팅, AI 멤버 글, 비회원과 새 회원의 글과 댓글에서 외부 연락 수단(URL, 메신저 아이디, 국내 전화번호)을 찾는다. */
public final class ContactFilter {

    private static final Pattern URL_PATTERN =
            Pattern.compile(
                    "(?i)(?:https?://|www\\.)\\S+"
                            + "|\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                            + "(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})(?::\\d{1,5})?(?:/\\S*)?"
                            + "|\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}"
                            + "(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?::\\d{1,5})?(?:/\\S*)?");
    private static final Pattern MESSENGER_IDENTIFIER_PATTERN =
            Pattern.compile(
                    "(?i)(?:\\bt\\.me/\\S+|\\btelegram\\b|\\bopen\\.kakao\\.com/\\S+|(?<![\\w.])@[a-z0-9_]{3,})");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile(
                    "(?<![0-9])(?:\\+82[ .-]?(?:1[016789]|2|[3-6][1-5]|70)|0(?:1[016789]|2|[3-6][1-5]|70))[ .-]?[0-9]{3,4}[ .-]?[0-9]{4}(?![0-9])");

    private static final Pattern MESSENGER_LINK_PATTERN =
            Pattern.compile("(?i)\\bt\\.me/\\S+|\\bopen\\.kakao\\.com/\\S+");

    /**
     * 메신저 이름 바로 뒤에 붙은 아이디 (IKA-122). "카카오톡: TAM999", "카톡 아이디는 tam999", "텔레그램 @Rtam999" 같은 광고 표기를
     * 잡는다. 아이디는 영문자로 시작하고 4자 이상이어야 한다. "라인 100m", "카톡 ok" 같은 일상 표현을 피하려는 기준이다.
     */
    private static final Pattern MESSENGER_LABEL_PATTERN =
            Pattern.compile(
                    "(?i)(?:카카오톡|카톡|카카오|텔레그램|텔레|라인|위챗|왓츠앱|kakaotalk|kakao|telegram|wechat|whatsapp)"
                            + "[\\s:：=@]*(?:아이디|id)?[\\s:：=@은는]*"
                            + "[a-z][a-z0-9_.-]{3,}");

    /**
     * 글과 댓글용 {@code @아이디}. 라이브톡용 {@link #MESSENGER_IDENTIFIER_PATTERN}과 달리 "telegram" 단어만으로는 걸지
     * 않는다. 메신저 이름은 아이디가 함께 있을 때만 {@link #MESSENGER_LABEL_PATTERN}이 잡는다.
     */
    private static final Pattern POSTING_IDENTIFIER_PATTERN =
            Pattern.compile("(?i)(?<![\\w.])@[a-z0-9_]{3,}");

    /**
     * 글과 댓글 검사 전에 지울 주소. 라이브톡용 {@link #URL_PATTERN}과 같지만 도메인 바로 뒤에 글자나 밑줄이 이어지면 주소로 보지 않는다.
     * "rider.id_123" 같은 아이디의 앞부분만 주소로 지워 아이디를 놓치지 않게 하려는 경계다.
     */
    private static final Pattern POSTING_URL_PATTERN =
            Pattern.compile(
                    "(?i)(?:https?://|www\\.)\\S+"
                            + "|\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                            + "(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})(?![\\w-])(?::\\d{1,5})?(?:/\\S*)?");

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z]+);");

    private ContactFilter() {}

    /**
     * 글과 댓글 본문에서 찾은 연락처 종류 (IKA-122). 없으면 빈 값.
     *
     * <p>메신저 링크를 먼저 보고, 나머지는 HTML 태그와 주소를 지운 뒤 본다. 사진 주소 안의 숫자가 전화번호로, 유튜브 채널 주소의 @가 메신저 아이디로 걸리지
     * 않게 하려는 순서다. 일반 주소(URL) 자체는 연락처로 보지 않는다. 리조트 홈페이지나 영상 링크를 거는 글이 흔해서다.
     */
    public static Optional<String> findPostingContact(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        if (MESSENGER_LINK_PATTERN.matcher(text).find()) return Optional.of("메신저 링크");
        String plain =
                POSTING_URL_PATTERN
                        .matcher(ENTITY.matcher(TAG.matcher(text).replaceAll(" ")).replaceAll(" "))
                        .replaceAll(" ");
        if (MESSENGER_LABEL_PATTERN.matcher(plain).find()
                || POSTING_IDENTIFIER_PATTERN.matcher(plain).find()) {
            return Optional.of("메신저 ID");
        }
        if (PHONE_PATTERN.matcher(plain).find()) {
            return Optional.of("전화번호");
        }
        return Optional.empty();
    }

    /** URL 또는 메신저 아이디 */
    public static boolean containsLinkOrMessenger(String content) {
        return URL_PATTERN.matcher(content).find()
                || MESSENGER_IDENTIFIER_PATTERN.matcher(content).find();
    }

    public static boolean containsPhone(String content) {
        return PHONE_PATTERN.matcher(content).find();
    }

    public static boolean containsAnyContact(String content) {
        return containsLinkOrMessenger(content) || containsPhone(content);
    }
}
