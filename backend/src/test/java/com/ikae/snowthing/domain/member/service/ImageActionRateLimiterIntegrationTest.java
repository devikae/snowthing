package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ikae.snowthing.domain.member.service.ProfileActionRateLimiter.Action;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@SpringBootTest
class ImageActionRateLimiterIntegrationTest {
    @Autowired ProfileActionRateLimiter limiter;
    @Autowired JdbcTemplate jdbc;
    private String member;
    private final LocalDateTime mature = LocalDateTime.now().minusDays(10);

    @BeforeEach
    void setup() {
        member = UUID.randomUUID() + "@test.invalid";
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS request_budget_window (budget_key VARCHAR(100) PRIMARY KEY, used_count INT NOT NULL, expires_at_millis BIGINT NOT NULL)");
        jdbc.update("DELETE FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:%'");
    }

    private void seed(String key, int used) {
        jdbc.update(
                "INSERT INTO request_budget_window VALUES(?,?,FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))*1000)+86400000) ON DUPLICATE KEY UPDATE used_count=VALUES(used_count)",
                key,
                used);
    }

    private int used(String key) {
        return jdbc.queryForObject(
                "SELECT used_count FROM request_budget_window WHERE budget_key=?",
                Integer.class,
                key);
    }

    @Test
    void exhaustingPostBudgetPreservesProfileReservationAndRejectedRequestSpendsNothing() {
        seed("snowthing:limits:post-images:global:day", 970);
        seed("snowthing:limits:images:global:day", 970);
        assertThatThrownBy(() -> limiter.checkImage(member, Action.POST_IMAGE, mature, "192.0.2.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_GLOBAL_LIMIT);
        assertThat(used("snowthing:limits:images:global:day")).isEqualTo(970);
        limiter.checkImage(member, Action.PHOTO, mature, "192.0.2.1");
        assertThat(used("snowthing:limits:images:global:day")).isEqualTo(971);
        assertThat(used("snowthing:limits:photos:global:day")).isEqualTo(1);
    }

    @Test
    void exhaustingProfileBudgetCannotBlockPostUploads() {
        seed("snowthing:limits:photos:global:day", 30);
        seed("snowthing:limits:images:global:day", 30);
        assertThatThrownBy(() -> limiter.checkImage(member, Action.PHOTO, mature, "192.0.2.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_GLOBAL_LIMIT);
        limiter.checkImage(member, Action.POST_IMAGE, mature, "192.0.2.1");
        assertThat(used("snowthing:limits:post-images:global:day")).isEqualTo(1);
    }

    @Test
    void newAccountsHaveLowerBudgetAndMatureAccountsKeepExistingAllowance() {
        var recent = LocalDateTime.now();
        for (int i = 0; i < 5; i++)
            limiter.checkImage(member, Action.POST_IMAGE, recent, "192.0.2.1");
        assertThatThrownBy(() -> limiter.checkImage(member, Action.POST_IMAGE, recent, "192.0.2.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROFILE_ACTION_LIMIT);
        assertThat(used("snowthing:limits:images:global:hour")).isEqualTo(5);
        limiter.checkImage(member, Action.POST_IMAGE, mature, "192.0.2.1");
        assertThat(used("snowthing:limits:images:global:hour")).isEqualTo(6);
    }

    @Test
    void newProfileChangesAreLimitedIncludingResetRequests() {
        limiter.checkImage(member, Action.PHOTO, LocalDateTime.now(), "192.0.2.1");
        assertThatThrownBy(
                        () ->
                                limiter.checkImage(
                                        member, Action.PHOTO, LocalDateTime.now(), "192.0.2.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROFILE_ACTION_LIMIT);
    }

    @Test
    void ipv4AliasesShareOneBucketAndInvalidHostsUseConservativeUnknownBucket() {
        limiter.checkImage(member, Action.POST_IMAGE, mature, "192.0.2.1");
        String key =
                jdbc.queryForObject(
                        "SELECT budget_key FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:ip-post-image:%'",
                        String.class);
        seed(key, 60);
        assertThatThrownBy(
                        () ->
                                limiter.checkImage(
                                        "alias-" + member,
                                        Action.POST_IMAGE,
                                        mature,
                                        "192.000.002.001"))
                .isInstanceOf(CustomException.class);
        limiter.checkImage("unknown-" + member, Action.POST_IMAGE, mature, null);
        String unknownKey =
                jdbc.queryForObject(
                        "SELECT budget_key FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:ip-post-image:%' AND budget_key<>?",
                        String.class, key);
        seed(unknownKey, 60);
        for (String invalid : java.util.List.of("face", "invalid.example", "999.0.0.1"))
            assertThatThrownBy(
                            () ->
                                    limiter.checkImage(
                                            "invalid-" + member,
                                            Action.POST_IMAGE,
                                            mature,
                                            invalid))
                    .isInstanceOf(CustomException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT MAX(CHAR_LENGTH(budget_key)) FROM request_budget_window",
                                Integer.class))
                .isLessThanOrEqualTo(100);
    }

    @Test
    void ipBudgetIsSharedAcrossAccountsAndIpv6PrivacyAddresses() {
        limiter.checkImage(member, Action.POST_IMAGE, mature, "2001:db8:47::1");
        String key =
                jdbc.queryForObject(
                        "SELECT budget_key FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:ip-post-image:%'",
                        String.class);
        seed(key, 60);
        assertThatThrownBy(
                        () ->
                                limiter.checkImage(
                                        "second-" + member,
                                        Action.POST_IMAGE,
                                        mature,
                                        "2001:db8:47::abcd"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROFILE_ACTION_LIMIT);
        assertThat(used("snowthing:limits:images:global:hour")).isEqualTo(1);
        assertThat(key).doesNotContain("2001:").hasSizeLessThanOrEqualTo(100);
        limiter.checkImage("second-" + member, Action.POST_IMAGE, mature, "2001:db8:48::1");
    }
}
