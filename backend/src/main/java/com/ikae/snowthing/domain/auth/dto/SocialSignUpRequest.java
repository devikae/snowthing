package com.ikae.snowthing.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.ikae.snowthing.domain.member.dto.RegistrationConsentRequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialSignUpRequest implements RegistrationConsentRequest {
    @NotNull @AssertTrue private Boolean ageConfirmed;
    @NotNull @AssertTrue private Boolean privacyAgreed;
    @NotNull @AssertTrue private Boolean termsAgreed;
    @NotNull private Boolean marketingEmailAgreed;
    @NotBlank private String privacyVersion;
    @NotBlank private String termsVersion;
}
