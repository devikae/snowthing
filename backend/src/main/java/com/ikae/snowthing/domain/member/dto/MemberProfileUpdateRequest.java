package com.ikae.snowthing.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class MemberProfileUpdateRequest {

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
    public MemberProfileUpdateRequest(String nickname, String bio, String departureRegion, List<Long> resortIds, List<Long> ridingStyleIds) {
        this.nickname = nickname;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.resortIds = resortIds != null ? new ArrayList<>(resortIds) : new ArrayList<>();
        this.ridingStyleIds = ridingStyleIds != null ? new ArrayList<>(ridingStyleIds) : new ArrayList<>();
    }

    public List<Long> getResortIds() {
        return List.copyOf(resortIds);
    }

    public List<Long> getRidingStyleIds() {
        return List.copyOf(ridingStyleIds);
    }
}
