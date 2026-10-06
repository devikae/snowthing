package com.ikae.snowthing.domain.email.entity;

public enum EmailVerificationStatus {
    PENDING,
    SENT,
    VERIFIED,
    CONSUMED,
    SEND_FAILED,
    FAILED,
    EXPIRED
}
