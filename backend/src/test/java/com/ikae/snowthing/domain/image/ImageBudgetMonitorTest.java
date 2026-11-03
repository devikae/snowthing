package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.image.service.ImageBudgetMonitor;
import com.ikae.snowthing.domain.member.service.ProfileActionRateLimiter.Action;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ImageBudgetMonitorTest {
    @Test
    void concurrentDenialsAreAggregatedIntoBoundedEmfEventsWithoutIdentifyingData()
            throws Exception {
        var mapper = new ObjectMapper();
        var clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC);
        var monitor = new ImageBudgetMonitor(clock, mapper);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        var logger = (Logger) LoggerFactory.getLogger("image.budget");
        logger.addAppender(appender);
        try {
            java.util.stream.IntStream.range(0, 10000)
                    .parallel()
                    .forEach(i -> monitor.rejected(Action.POST_IMAGE));
            monitor.rejected(Action.PHOTO);
            monitor.flush();
            monitor.flush();
            assertThat(appender.list).hasSize(2);
            long total = 0;
            for (var event : appender.list) {
                var json = mapper.readTree(event.getFormattedMessage());
                total += json.get("GlobalBudgetRejected").asLong();
                assertThat(json.path("_aws").path("Timestamp").asLong()).isEqualTo(clock.millis());
                assertThat(
                                json.path("_aws")
                                        .path("CloudWatchMetrics")
                                        .get(0)
                                        .path("Namespace")
                                        .asText())
                        .isEqualTo("Snowthing/Images");
                assertThat(json.size()).isEqualTo(3);
                assertThat(json.get("Action").asText()).isIn("PHOTO", "POST_IMAGE");
            }
            assertThat(total).isEqualTo(10001);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
