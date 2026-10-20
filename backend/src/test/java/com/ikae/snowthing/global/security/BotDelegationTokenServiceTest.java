package com.ikae.snowthing.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.global.config.BotProperties;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class BotDelegationTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issueAndValidateReturnsBoundReadOnlyClaims() {
        BotDelegationTokenService service = serviceAt(NOW);

        BotDelegationClaims claims =
                service.validate(service.issue("member-public-id", "chat-message-id"));

        assertThat(claims.subject()).isEqualTo("member-public-id");
        assertThat(claims.chatMessageId()).isEqualTo("chat-message-id");
        assertThat(claims.scopes()).containsExactly(BotDelegationTokenService.READ_SCOPE);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void tamperedTokenIsRejected() {
        BotDelegationTokenService service = serviceAt(NOW);
        String token = service.issue("member-public-id", "chat-message-id");

        assertInvalid(() -> service.validate(token + "x"));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = serviceAt(NOW).issue("member-public-id", "chat-message-id");

        assertInvalid(() -> serviceAt(NOW.plusSeconds(61)).validate(token));
    }

    @Test
    void blankIdentityCannotBeIssued() {
        assertThatThrownBy(() -> serviceAt(NOW).issue(" ", "chat-message-id"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void shortDelegationSecretFailsEagerlyWhenBotEnabled() {
        BotProperties invalidProperties =
                new BotProperties(
                        true,
                        URI.create("http://127.0.0.1:3100"),
                        SECRET,
                        "short-secret",
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(15));

        assertThatThrownBy(
                        () ->
                                new BotDelegationTokenService(
                                        invalidProperties,
                                        new ObjectMapper(),
                                        Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snowthing.bot.delegation-secret");
    }

    @Test
    void shortInternalSecretFailsEagerlyWhenBotEnabled() {
        BotProperties invalidProperties =
                new BotProperties(
                        true,
                        URI.create("http://127.0.0.1:3100"),
                        "short-secret",
                        SECRET,
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(15));

        assertThatThrownBy(
                        () ->
                                new BotDelegationTokenService(
                                        invalidProperties,
                                        new ObjectMapper(),
                                        Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snowthing.bot.internal-secret");
    }

    @Test
    void disabledServiceRejectsValidationWithInvalidCredentialsInsteadOfServerError() {
        BotProperties disabledProperties =
                new BotProperties(
                        false,
                        URI.create("http://127.0.0.1:3100"),
                        "",
                        "",
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(15));
        BotDelegationTokenService disabledService =
                new BotDelegationTokenService(
                        disabledProperties, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        String token = serviceAt(NOW).issue("member-public-id", "chat-message-id");

        assertInvalid(() -> disabledService.validate(token));
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    private BotDelegationTokenService serviceAt(Instant instant) {
        BotProperties properties =
                new BotProperties(
                        true,
                        URI.create("http://127.0.0.1:3100"),
                        SECRET,
                        SECRET,
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(15));
        return new BotDelegationTokenService(
                properties, new ObjectMapper(), Clock.fixed(instant, ZoneOffset.UTC));
    }
}
