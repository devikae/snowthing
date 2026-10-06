package com.ikae.snowthing.domain.email.dto;

public record EmailVerificationTokenResponse(String token, long expiresInSeconds) {}
