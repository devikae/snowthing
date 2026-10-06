package com.ikae.snowthing.domain.email.dto;

public record EmailVerificationSendResponse(
        String requestId, long expiresInSeconds, long resendAvailableInSeconds) {}
