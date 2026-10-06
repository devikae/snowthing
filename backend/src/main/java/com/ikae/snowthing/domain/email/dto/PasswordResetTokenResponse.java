package com.ikae.snowthing.domain.email.dto;

public record PasswordResetTokenResponse(String resetToken, long expiresInSeconds) {}
