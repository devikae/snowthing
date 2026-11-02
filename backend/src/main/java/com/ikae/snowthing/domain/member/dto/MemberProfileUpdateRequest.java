package com.ikae.snowthing.domain.member.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.ikae.snowthing.domain.member.validation.NicknamePolicy;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberProfileUpdateRequest {

    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    @Pattern(regexp = NicknamePolicy.REGEXP, message = NicknamePolicy.MESSAGE)
    private String nickname;

    @Size(max = 255)
    private String bio;

    @Size(max = 100)
    private String departureRegion;

    @NotNull
    @Size(max = 2)
    private List<@NotNull @Positive Long> resortIds;

    @NotNull
    @Size(max = 50)
    private List<@NotNull @Positive Long> ridingStyleIds;

    @Builder
    MemberProfileUpdateRequest(
            String nickname,
            String bio,
            String departureRegion,
            List<Long> resortIds,
            List<Long> ridingStyleIds) {
        this.nickname = nickname;
        this.bio = bio;
        this.departureRegion = departureRegion;
        this.resortIds = resortIds != null ? new ArrayList<>(resortIds) : new ArrayList<>();
        this.ridingStyleIds =
                ridingStyleIds != null ? new ArrayList<>(ridingStyleIds) : new ArrayList<>();
    }

    public List<Long> getResortIds() {
        return resortIds != null ? List.copyOf(resortIds) : null;
    }

    public List<Long> getRidingStyleIds() {
        return ridingStyleIds != null ? List.copyOf(ridingStyleIds) : null;
    }
}
