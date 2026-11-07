package com.ikae.snowthing.domain.member.validation;

import java.util.List;

public final class NicknamePolicy {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 10;
    public static final String REGEXP = "^[a-zA-Z0-9가-힣]{2,10}$";
    public static final String MESSAGE = "닉네임은 2자 이상 10자 이하의 한글, 영문, 숫자이어야 합니다.";

    public static final List<String> RESERVED_WORDS = List.of("운영자", "관리자");

    private NicknamePolicy() {}

    public static boolean isReserved(String nickname) {
        return nickname != null && RESERVED_WORDS.stream().anyMatch(nickname::contains);
    }
}
