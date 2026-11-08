package com.ikae.snowthing.domain.member.service;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@Component
public class RegistrationPrivacyPolicy {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String version;
    private final String termsVersion;

    public RegistrationPrivacyPolicy() {
        try (var manifest =
                new ClassPathResource("registration-policies/current.json").getInputStream()) {
            JsonNode selected = mapper.readTree(manifest);
            version = verifiedSnapshot(selected.path("version").asText());
            termsVersion = verifiedSnapshot(selected.path("termsVersion").asText());
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.REGISTRATION_POLICY_UNAVAILABLE, exception);
        }
    }

    private String verifiedSnapshot(String selected) throws IOException {
        if (!selected.matches("[a-z0-9-]+"))
            throw new CustomException(ErrorCode.REGISTRATION_POLICY_UNAVAILABLE);
        try (var snapshot =
                new ClassPathResource("registration-policies/" + selected + ".json")
                        .getInputStream()) {
            if (!selected.equals(mapper.readTree(snapshot).path("version").asText()))
                throw new CustomException(ErrorCode.REGISTRATION_POLICY_UNAVAILABLE);
        }
        return selected;
    }

    public String version() {
        return version;
    }

    public String termsVersion() {
        return termsVersion;
    }

    public void validate(String acceptedPrivacy, String acceptedTerms) {
        if (!version.equals(acceptedPrivacy) || !termsVersion.equals(acceptedTerms))
            throw new CustomException(ErrorCode.REGISTRATION_POLICY_CHANGED);
    }
}
