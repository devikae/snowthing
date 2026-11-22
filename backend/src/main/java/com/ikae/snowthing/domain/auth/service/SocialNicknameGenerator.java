package com.ikae.snowthing.domain.auth.service;

import java.security.SecureRandom;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 소셜 가입 회원의 처음 닉네임. "형용사 + 명사 + 두 자리 숫자"로 만든다(예: 졸린펭귄27).
 *
 * <p>카카오, 네이버가 주는 이름은 실명인 경우가 많아 닉네임으로 쓰지 않는다. 가입 뒤 프로필에서 바꿀 수 있다. 단어는 4자 이하라 결과는 언제나 닉네임 규칙(2~10자
 * 한글, 영문, 숫자) 안이다. 운영진처럼 보이는 말은 넣지 않는다.
 */
@Component
public class SocialNicknameGenerator {

    static final String[] ADJECTIVES = {
        "신난", "졸린", "배고픈", "용감한", "느긋한", "수줍은", "빠른", "행복한", "씩씩한", "포근한",
        "들뜬", "차분한", "엉뚱한", "날쌘", "꿋꿋한", "상쾌한", "명랑한", "우아한", "흥많은", "진지한",
        "꼼꼼한", "말랑한", "눈부신", "겁없는", "신중한", "반짝이는", "두근대는", "여유로운", "부지런한", "조심스런"
    };

    static final String[] NOUNS = {
        "눈사람", "펭귄", "북극곰", "눈토끼", "산양", "수달", "다람쥐", "부엉이", "북극여우", "물개",
        "보더", "스키어", "카버", "트릭러", "모굴러", "눈꽃", "고드름", "눈송이", "핫초코", "호빵",
        "붕어빵", "어묵", "귤", "털모자", "고글", "곤돌라", "리프트", "썰매", "설인", "눈보라"
    };

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return ADJECTIVES[secureRandom.nextInt(ADJECTIVES.length)]
                + NOUNS[secureRandom.nextInt(NOUNS.length)]
                + String.format(Locale.ROOT, "%02d", secureRandom.nextInt(100));
    }
}
