package com.ikae.snowthing.domain.member.dto;

public interface RegistrationConsentRequest {
    Boolean getAgeConfirmed();

    Boolean getPrivacyAgreed();

    Boolean getTermsAgreed();

    Boolean getMarketingEmailAgreed();

    String getPrivacyVersion();

    String getTermsVersion();
}
