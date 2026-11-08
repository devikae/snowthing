package com.ikae.snowthing.domain.push.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PushEndpointPolicyTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://fcm.googleapis.com/fcm/send/abc:def",
                "https://web.push.apple.com/QGx1",
                "https://updates.push.services.mozilla.com/wpush/v2/gAAA",
                "https://wns2-par02p.notify.windows.com/w/?token=abc"
            })
    void allowsKnownPushServices(String endpoint) {
        assertThat(PushEndpointPolicy.parse(endpoint)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://fcm.googleapis.com/fcm/send/abc",
                "https://169.254.169.254/latest/meta-data",
                "https://localhost:8080/api",
                "https://evilfcm.googleapis.com.attacker.com/x",
                "https://notfcm.googleapis.com.evil/x",
                "https://fcm.googleapis.com:8443/fcm/send/abc",
                "https://user@fcm.googleapis.com/fcm/send/abc",
                "https://attacker-push.apple.com.evil.com/x",
                "not a url"
            })
    void rejectsOtherHosts(String endpoint) {
        assertThat(PushEndpointPolicy.parse(endpoint)).isNull();
    }
}
