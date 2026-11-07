package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.repository.MemberRegistrationConsentRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class RegistrationConsentServiceTest {

    private final RegistrationPrivacyPolicy policy = new RegistrationPrivacyPolicy();
    private final RegistrationConsentService service =
            new RegistrationConsentService(mock(MemberRegistrationConsentRepository.class), policy);

    @Test
    @DisplayName("만 14세 이상 확인이 없으면 다른 동의를 모두 해도 가입할 수 없다")
    void rejectsWithoutAgeConfirmation() {
        for (Boolean age : new Boolean[] {false, null}) {
            var request =
                    MemberSignUpRequest.builder()
                            .ageConfirmed(age)
                            .privacyAgreed(true)
                            .termsAgreed(true)
                            .marketingEmailAgreed(false)
                            .privacyVersion(policy.version())
                            .termsVersion(policy.termsVersion())
                            .build();

            assertThatThrownBy(() -> service.validate(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }
}
