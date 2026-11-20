package com.ikae.snowthing.domain.chat.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 라이브톡 비회원 닉네임. "형용사 + 명사 + #네 자리 번호"로 만든다(예: 졸린펭귄#4821).
 *
 * <p>같은 IP는 같은 닉네임을 받는다. IP를 SHA-256으로 섞은 뒤 형용사, 명사, 번호를 서로 다른 자리에서 뽑아 셋이 서로 엮이지 않게 한다. 운영진처럼 보일 수
 * 있는 말(운영자, 관리자, 패트롤 등)은 넣지 않는다.
 */
final class AnonymousNickname {

    static final String[] ADJECTIVES = {
        "신난", "졸린", "배고픈", "용감한", "느긋한", "수줍은", "빠른", "조심스런", "행복한", "씩씩한",
        "반짝이는", "포근한", "들뜬", "차분한", "엉뚱한", "부지런한", "날쌘", "꿋꿋한", "상쾌한", "두근대는",
        "겁없는", "신중한", "명랑한", "우아한", "흥많은", "여유로운", "진지한", "꼼꼼한", "말랑한", "눈부신"
    };

    static final String[] NOUNS = {
        "눈사람", "펭귄", "북극곰", "눈토끼", "산양", "수달", "다람쥐", "부엉이", "북극여우", "물개",
        "스노보더", "스키어", "카버", "파우더헌터", "트릭러", "모굴러", "슬로프요정", "덕스탠스", "카빙꿈나무", "시즌권러버",
        "설질지킴이", "안전라이더", "눈꽃", "고드름", "눈송이", "핫초코", "호빵", "붕어빵", "군고구마", "어묵",
        "귤", "털모자", "벙어리장갑", "고글", "스키왁스", "곤돌라", "리프트", "썰매", "설인", "눈보라"
    };

    private AnonymousNickname() {}

    static String of(String clientIp) {
        ByteBuffer digest = ByteBuffer.wrap(sha256(clientIp == null ? "" : clientIp));
        String adjective = ADJECTIVES[Math.floorMod(digest.getInt(0), ADJECTIVES.length)];
        String noun = NOUNS[Math.floorMod(digest.getInt(4), NOUNS.length)];
        int number = Math.floorMod(digest.getInt(8), 10_000);
        // 서버 기본 로케일에 따라 다른 숫자 문자가 나오지 않게 ROOT로 고정한다.
        return adjective + noun + "#" + String.format(Locale.ROOT, "%04d", number);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 쓸 수 없다", exception);
        }
    }
}
