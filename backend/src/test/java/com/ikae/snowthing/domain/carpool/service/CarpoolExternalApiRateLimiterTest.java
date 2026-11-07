package com.ikae.snowthing.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class CarpoolExternalApiRateLimiterTest {

    private static final String CLIENT_IP = "192.0.2.10";
    private static final int PREVIEW_REQUEST_LIMIT = 12;
    private static final int PLACE_REQUEST_LIMIT = 30;

    private final CarpoolExternalApiRateLimiter rateLimiter =
            new CarpoolExternalApiRateLimiter(5000, 20000);

    @Test
    void rejectsPreviewRequestsAfterPerMinuteLimit() {
        for (int count = 0; count < PREVIEW_REQUEST_LIMIT; count++) {
            rateLimiter.checkPreview(CLIENT_IP);
        }

        assertThatThrownBy(() -> rateLimiter.checkPreview(CLIENT_IP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CARPOOL_EXTERNAL_API_RATE_LIMIT);
    }

    @Test
    void keepsPlaceAndPreviewLimitsIndependent() {
        for (int count = 0; count < PLACE_REQUEST_LIMIT; count++) {
            rateLimiter.checkPlaceSearch(CLIENT_IP);
        }

        rateLimiter.checkPreview(CLIENT_IP);
        assertThatThrownBy(() -> rateLimiter.checkPlaceSearch(CLIENT_IP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CARPOOL_EXTERNAL_API_RATE_LIMIT);
    }

    @Test
    void groupsIpv6AddressesInTheSameSlash64() {
        for (int count = 0; count < PREVIEW_REQUEST_LIMIT; count++) {
            rateLimiter.checkPreview("2001:db8:1:2::" + Integer.toHexString(count + 1));
        }

        assertThatThrownBy(() -> rateLimiter.checkPreview("2001:db8:1:2::ffff"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CARPOOL_EXTERNAL_API_RATE_LIMIT);
    }

    @Test
    void rejectsAfterServiceWideDailyLimitAcrossClients() {
        CarpoolExternalApiRateLimiter limited = new CarpoolExternalApiRateLimiter(3, 20000);
        limited.checkPreview("192.0.2.1");
        limited.checkPreview("192.0.2.2");
        limited.checkPreview("192.0.2.3");

        assertThatThrownBy(() -> limited.checkPreview("192.0.2.4"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CARPOOL_EXTERNAL_API_RATE_LIMIT);
    }
}
