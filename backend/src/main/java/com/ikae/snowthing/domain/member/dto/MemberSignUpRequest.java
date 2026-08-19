package com.ikae.snowthing.domain.member.dto;

import com.ikae.snowthing.domain.member.entity.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class MemberSignUpRequest {

    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Pattern(
            regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$",
            message = "올바른 이메일 형식(예: user@domain.com)이어야 합니다."
    )
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$",
            message = "비밀번호는 최소 8자 이상이며, 영문 대문자 1개 이상과 특수문자 1개 이상을 포함해야 합니다."
    )
    private String password;

    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    @Pattern(
            regexp = "^[a-zA-Z0-9가-힣]{2,10}$",
            message = "닉네임은 2자 이상 10자 이하의 한글, 영문, 숫자이어야 합니다."
    )
    private String nickname;

    private String bio;

    private String departureRegion;

    private List<Long> resortIds = new ArrayList<>();

    private List<Long> ridingStyleIds = new ArrayList<>();

    @Builder
    public MemberSignUpRequest(String email, String password, String nickname, String bio, String departureRegion, List<Long> resortIds, List<Long> ridingStyleIds) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.resortIds = resortIds != null ? resortIds : new ArrayList<>();
        this.ridingStyleIds = ridingStyleIds != null ? ridingStyleIds : new ArrayList<>();
    }

    public Member toEntity(String encodedPassword) {
        return Member.builder()
                .email(this.email)
                .password(encodedPassword)
                .nickname(this.nickname)
                .bio(this.bio)
                .departureRegion(this.departureRegion)
                .build();
    }
}
