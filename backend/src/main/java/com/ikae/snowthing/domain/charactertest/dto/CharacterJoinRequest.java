package com.ikae.snowthing.domain.charactertest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.ikae.snowthing.domain.charactertest.entity.CharacterSport;
import com.ikae.snowthing.domain.charactertest.model.CharacterAnswers;
import com.ikae.snowthing.domain.member.validation.NicknamePolicy;

/**
 * 모임 생성과 참여에 함께 쓴다. 금칙어 검사는 서비스에서 한다. groupName은 생성에서만 쓰고 참여에서는 무시한다. 비우면 "닉네임의 모임"이 붙는다. 앞뒤 공백을 뺀
 * 길이와 허용 글자는 서비스에서 다시 본다.
 */
public record CharacterJoinRequest(
        @NotNull(message = NAME_MESSAGE)
                @Pattern(regexp = NicknamePolicy.REGEXP, message = NAME_MESSAGE)
                String nickname,
        @NotNull(message = "스키 또는 보드를 골라 주세요.") CharacterSport sport,
        @NotNull(message = "답변을 입력해 주세요.")
                @Pattern(regexp = CharacterAnswers.REGEXP, message = "답변 형식이 올바르지 않습니다.")
                String answers,
        @Size(max = 40, message = GROUP_NAME_MESSAGE) String groupName) {

    public static final String GROUP_NAME_MESSAGE =
            "모임 이름은 2자 이상 20자 이하의 한글, 영문, 숫자, 띄어쓰기로 입력해 주세요.";

    public CharacterJoinRequest(String nickname, CharacterSport sport, String answers) {
        this(nickname, sport, answers, null);
    }

    private static final String NAME_MESSAGE = "이름은 2자 이상 10자 이하의 한글, 영문, 숫자이어야 합니다.";
}
