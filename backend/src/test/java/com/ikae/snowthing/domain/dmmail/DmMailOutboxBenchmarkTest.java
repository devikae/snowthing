package com.ikae.snowthing.domain.dmmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Outbox: 쪽지 트랜잭션은 발송 요청만 쓰고, 워커 한 개가 1초 간격으로 폴링해 보낸다. {@code -PincludeBenchmark}로만 돈다. */
@Tag("benchmark")
@SpringBootTest(
        properties = {
            "snowthing.dm-mail.enqueue-enabled=true",
            "dm-mail-test.attempt-timeout-ms=3000",
            "dm-mail-test.call-timeout-ms=5000",
            "snowthing.dm-mail.send-enabled=true",
            "snowthing.dm-mail.initial-delay-ms=0",
            "snowthing.dm-mail.poll-interval-ms=1000"
        })
@Import(DmMailTestConfig.class)
class DmMailOutboxBenchmarkTest extends DmMailBenchmarkSupport {

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("Outbox: 쪽지 응답은 SES 지연과 분리되고, 알림은 워커 처리량만큼 늦어진다")
    void measure() throws Exception {
        Map<String, Object> result = measureSend("outbox");
        result.put("delivery", measureDelivery(Duration.ofMinutes(10)));
        write("dm-mail-outbox", result);
        assertThat(DmMailTestConfig.FAKE_SES.accepted()).isEqualTo(N);
    }
}
