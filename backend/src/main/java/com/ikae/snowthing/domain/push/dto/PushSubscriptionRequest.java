package com.ikae.snowthing.domain.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 브라우저 {@code PushSubscription.toJSON()} 모양 그대로 받는다. expirationTime은 쓰지 않아 무시한다. */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1024) String endpoint, @NotNull @Valid Keys keys) {

    public record Keys(
            @NotBlank @Size(max = 128) String p256dh, @NotBlank @Size(max = 64) String auth) {}
}
