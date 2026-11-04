package com.ikae.snowthing.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DiscordNotifierTest {

    private static final String ALERT = "https://discord.test/api/webhooks/1/alert";
    private static final String EVENT = "https://discord.test/api/webhooks/2/event";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private DiscordNotifier notifier(String alert, String event) {
        return new DiscordNotifier(
                builder.build(),
                new DiscordProperties(alert, event, Duration.ofSeconds(3)),
                Runnable::run);
    }

    @Test
    @DisplayName("채널별 웹훅으로 보내고 멘션은 막는다")
    void sendsToChannelWebhookWithoutMentions() {
        server.expect(requestTo(ALERT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.content").value("@everyone 장애"))
                .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty())
                .andRespond(withSuccess());
        server.expect(requestTo(EVENT)).andRespond(withSuccess());

        DiscordNotifier notifier = notifier(ALERT, EVENT);
        notifier.alert("@everyone 장애");
        notifier.event("가입");

        server.verify();
    }

    @Test
    @DisplayName("웹훅 URL이 비어 있으면 아무 요청도 보내지 않는다")
    void skipsWhenWebhookBlank() {
        DiscordNotifier notifier = notifier(" ", null);

        notifier.alert("장애");
        notifier.event("가입");

        server.verify();
        assertThat(notifier.eventEnabled()).isFalse();
    }

    @Test
    @DisplayName("Discord 오류 응답은 호출자에게 전파하지 않는다")
    void swallowsDiscordFailure() {
        server.expect(requestTo(ALERT)).andRespond(withServerError());

        assertThatCode(() -> notifier(ALERT, "").alert("장애")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("대기열이 가득 차도 호출자에게 예외를 던지지 않는다")
    void swallowsRejectedExecution() {
        DiscordNotifier notifier =
                new DiscordNotifier(
                        builder.build(),
                        new DiscordProperties(ALERT, "", Duration.ofSeconds(3)),
                        task -> {
                            throw new RejectedExecutionException();
                        });

        assertThatCode(() -> notifier.alert("장애")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Discord 길이 제한을 넘는 본문은 자른다")
    void truncatesLongContent() {
        String truncated = DiscordNotifier.truncate("가".repeat(5000));

        assertThat(truncated).hasSize(DiscordNotifier.MAX_CONTENT_LENGTH).endsWith("…");
        assertThat(DiscordNotifier.truncate("짧은 글")).isEqualTo("짧은 글");
    }
}
