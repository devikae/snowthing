package com.ikae.snowthing.domain.dmmail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 동기 기준선: 쪽지 트랜잭션 안에서 SES를 바로 부른다. {@code -PincludeBenchmark}로만 돈다. */
@Tag("benchmark")
@SpringBootTest(
        properties = {
            "snowthing.dm-mail.enqueue-enabled=true",
            "dm-mail-test.attempt-timeout-ms=3000",
            "dm-mail-test.call-timeout-ms=5000",
            "snowthing.dm-mail.delivery-mode=sync-baseline"
        })
@Import(DmMailTestConfig.class)
class DmMailSyncBaselineBenchmarkTest extends DmMailBenchmarkSupport {

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("동기 기준선: SES 지연이 쪽지 응답에 그대로 더해진다")
    void measure() throws Exception {
        Map<String, Object> result = measureSend("sync-baseline");
        result.put("fakeSesRequests", DmMailTestConfig.FAKE_SES.requests());
        result.put("fakeSesAccepted", DmMailTestConfig.FAKE_SES.accepted());
        result.put(
                "outboxRows",
                jdbc.queryForObject("SELECT COUNT(*) FROM dm_mail_outbox", Long.class));
        write("dm-mail-sync-baseline", result);
        assertThat(DmMailTestConfig.FAKE_SES.requests()).isPositive();
    }
}
