package com.ikae.snowthing.domain.email.dto;

public record SignUpVerificationTokenResponse(String verificationToken, long expiresInSeconds) {}
