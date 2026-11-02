package com.ikae.snowthing.domain.member.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.validation.NicknamePolicy;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberSignUpRequest {

    @NotNull @AssertTrue private Boolean ageConfirmed;
    @NotNull @AssertTrue private Boolean privacyAgreed;
    @NotNull private Boolean marketingEmailAgreed;
    @NotBlank private String privacyVersion;

    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Pattern(
            regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$",
            message = "올바른 이메일 형식(예: user@domain.com)이어야 합니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다.")
    private String password;

    @NotBlank(message = "이메일 인증을 완료해 주세요.")
    private String emailVerificationToken;

    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    @Pattern(regexp = NicknamePolicy.REGEXP, message = NicknamePolicy.MESSAGE)
    private String nickname;

    private String bio;

    private String departureRegion;

    private List<Long> resortIds = new ArrayList<>();

    private List<Long> ridingStyleIds = new ArrayList<>();

    @Builder
    public MemberSignUpRequest(
            String email,
            String password,
            String emailVerificationToken,
            String nickname,
            String bio,
            String departureRegion,
            List<Long> resortIds,
            List<Long> ridingStyleIds,
            Boolean ageConfirmed,
            Boolean privacyAgreed,
            Boolean marketingEmailAgreed,
            String privacyVersion) {
        this.email = email;
        this.password = password;
        this.emailVerificationToken = emailVerificationToken;
        this.nickname = nickname;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.ageConfirmed = ageConfirmed;
        this.privacyAgreed = privacyAgreed;
        this.marketingEmailAgreed = marketingEmailAgreed;
        this.privacyVersion = privacyVersion;
        this.resortIds = resortIds != null ? new ArrayList<>(resortIds) : new ArrayList<>();
        this.ridingStyleIds =
                ridingStyleIds != null ? new ArrayList<>(ridingStyleIds) : new ArrayList<>();
    }

    public List<Long> getResortIds() {
        return List.copyOf(resortIds);
    }

    public List<Long> getRidingStyleIds() {
        return List.copyOf(ridingStyleIds);
    }

    public Member toEntity(String encodedPassword) {
        return toEntity(encodedPassword, this.email);
    }

    public Member toEntity(String encodedPassword, String normalizedEmail) {
        return Member.builder()
                .email(normalizedEmail)
                .password(encodedPassword)
                .nickname(this.nickname)
                .bio(this.bio)
                .departureRegion(this.departureRegion)
                .build();
    }
}
