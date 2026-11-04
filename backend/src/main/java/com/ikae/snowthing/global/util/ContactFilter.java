package com.ikae.snowthing.global.util;

import java.util.regex.Pattern;

/** 채팅·AI 멤버 글에서 외부 연락 수단(URL, 메신저 아이디, 국내 전화번호)을 찾는다. */
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

    private ContactFilter() {}

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
