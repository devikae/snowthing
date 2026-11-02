package com.ikae.snowthing.domain.member.service;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@Component
public class RegistrationPrivacyPolicy {
    private final String version;

    public RegistrationPrivacyPolicy() {
        var mapper = new ObjectMapper();
        try (var manifest =
                new ClassPathResource("registration-policies/current.json").getInputStream()) {
            String selected = mapper.readTree(manifest).path("version").asText();
            if (!selected.matches("[a-z0-9-]+"))
                throw new CustomException(ErrorCode.REGISTRATION_POLICY_UNAVAILABLE);
            try (var snapshot =
                    new ClassPathResource("registration-policies/" + selected + ".json")
                            .getInputStream()) {
                if (!selected.equals(mapper.readTree(snapshot).path("version").asText()))
                    throw new CustomException(ErrorCode.REGISTRATION_POLICY_UNAVAILABLE);
            }
            version = selected;
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.REGISTRATION_POLICY_UNAVAILABLE, exception);
        }
    }

    public String version() {
        return version;
    }

    public void validate(String accepted) {
        if (!version.equals(accepted))
            throw new CustomException(ErrorCode.REGISTRATION_POLICY_CHANGED);
    }
}
