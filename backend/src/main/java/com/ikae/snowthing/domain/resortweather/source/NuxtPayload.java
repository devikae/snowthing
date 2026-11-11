package com.ikae.snowthing.domain.resortweather.source;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Nuxt 2가 서버 렌더 HTML에 넣는 {@code window.__NUXT__=(function(a,b,...){return {...}}(v1,v2,...))} 상태를
 * 읽는다.
 *
 * <p>Nuxt 2(devalue)는 상태 안에서 두 번 이상 나오는 값을 함수 인자로 빼고 본문에는 인자 이름만 남긴다. 웰리힐리 상태에는 "0"~"4" 같은 짧은 숫자
 * 문자열이 여러 번 나와서, 기온이 그 값과 같아지면 {@code TEMP:"2"} 대신 {@code TEMP:j}처럼 이름만 온다. 이름이면 인자 목록에서 같은 순서의 값을
 * 찾아 돌려준다. JavaScript를 실행하지 않고 문자열 리터럴만 건너뛰며 괄호 깊이를 센다.
 */
final class NuxtPayload {

    private static final String PREFIX = "window.__NUXT__=(function(";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

    private final String source;
    private final List<String> params;
    private final int bodyStart;
    private List<String> args;

    private NuxtPayload(String source, List<String> params, int bodyStart) {
        this.source = source;
        this.params = params;
        this.bodyStart = bodyStart;
    }

    /** 함수 형태 상태가 없으면 null. 인자가 없는 {@code window.__NUXT__={...}} 형태도 null이 아니라 빈 인자로 다룬다. */
    static NuxtPayload find(String html) {
        int start = html.indexOf(PREFIX);
        if (start >= 0) {
            int paramsStart = start + PREFIX.length();
            int paramsEnd = html.indexOf(')', paramsStart);
            if (paramsEnd < 0) {
                return null;
            }
            String rawParams = html.substring(paramsStart, paramsEnd).trim();
            List<String> params =
                    rawParams.isEmpty() ? List.of() : List.of(rawParams.split("\\s*,\\s*"));
            return new NuxtPayload(html, params, paramsEnd + 1);
        }
        int plain = html.indexOf("window.__NUXT__=");
        return plain < 0 ? null : new NuxtPayload(html, List.of(), plain);
    }

    /** 상태 본문 시작 위치. 값 검색은 이 뒤에서만 한다. */
    int bodyStart() {
        return bodyStart;
    }

    /**
     * 값 토큰을 문자열로 바꾼다. 문자열 리터럴은 따옴표를 벗기고, 숫자는 그대로, 인자 이름이면 인자 값을 다시 해석한다. 그 밖(null, 객체 등)이면 null.
     */
    String resolve(String token) {
        String value = token.trim();
        if (IDENTIFIER.matcher(value).matches() && !isKeyword(value)) {
            int index = params.indexOf(value);
            List<String> values = args();
            if (index < 0 || index >= values.size()) {
                return null;
            }
            value = values.get(index).trim();
        }
        if (value.length() >= 2
                && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                && value.charAt(value.length() - 1) == value.charAt(0)) {
            return unescape(value.substring(1, value.length() - 1));
        }
        if (value.matches("-?\\d+(?:\\.\\d+)?")) {
            return value;
        }
        return null;
    }

    private static boolean isKeyword(String value) {
        return switch (value) {
            case "null", "true", "false", "undefined", "void" -> true;
            default -> false;
        };
    }

    /** 함수 본문을 건너뛴 뒤 호출 괄호 안의 인자를 최상위 쉼표로 나눈다. 처음 쓸 때 한 번만 계산한다. */
    private List<String> args() {
        if (args != null) {
            return args;
        }
        args = List.of();
        int bodyOpen = source.indexOf('{', bodyStart);
        if (bodyOpen < 0) {
            return args;
        }
        int bodyClose = matchingClose(bodyOpen);
        if (bodyClose < 0
                || bodyClose + 1 >= source.length()
                || source.charAt(bodyClose + 1) != '(') {
            return args;
        }
        List<String> values = new ArrayList<>();
        int depth = 0;
        int tokenStart = bodyClose + 2;
        for (int i = tokenStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipString(i);
                if (i < 0) {
                    return args;
                }
            } else if (c == '{' || c == '[' || c == '(') {
                depth++;
            } else if (c == '}' || c == ']' || c == ')') {
                if (depth == 0) {
                    values.add(source.substring(tokenStart, i));
                    args = List.copyOf(values);
                    return args;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                values.add(source.substring(tokenStart, i));
                tokenStart = i + 1;
            }
        }
        return args;
    }

    private int matchingClose(int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipString(i);
                if (i < 0) {
                    return -1;
                }
            } else if (c == '{' || c == '[' || c == '(') {
                depth++;
            } else if (c == '}' || c == ']' || c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 여는 따옴표 위치를 받아 닫는 따옴표 위치를 돌려준다. 닫히지 않으면 -1. */
    private int skipString(int open) {
        char quote = source.charAt(open);
        for (int i = open + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return -1;
    }

    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                out.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            if (next == 'u'
                    && i + 4 < raw.length()
                    && raw.substring(i + 1, i + 5).matches("[0-9a-fA-F]{4}")) {
                out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                i += 4;
            } else if (next == 'n') {
                out.append('\n');
            } else if (next == 't') {
                out.append('\t');
            } else {
                out.append(next);
            }
        }
        return out.toString();
    }
}
