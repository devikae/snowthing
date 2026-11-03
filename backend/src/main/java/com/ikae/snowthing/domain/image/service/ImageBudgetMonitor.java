package com.ikae.snowthing.domain.image.service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.service.ProfileActionRateLimiter.Action;

@Component
public class ImageBudgetMonitor {
    private final Clock clock;
    private final ObjectMapper mapper;
    private final Map<Action, AtomicLong> rejected =
            Map.of(Action.PHOTO, new AtomicLong(), Action.POST_IMAGE, new AtomicLong());

    public ImageBudgetMonitor(Clock clock, ObjectMapper mapper) {
        this.clock = clock;
        this.mapper = mapper;
    }

    public void rejected(Action action) {
        rejected.get(action).incrementAndGet();
    }

    // Bound log volume during abuse while preserving the rejection count. No network I/O on request
    // threads.
    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    @jakarta.annotation.PreDestroy
    public void flush() {
        rejected.forEach(
                (action, counter) -> {
                    long count = counter.getAndSet(0);
                    if (count == 0) return;
                    try {
                        LoggerFactory.getLogger("image.budget")
                                .info(
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "_aws",
                                                        Map.of(
                                                                "Timestamp",
                                                                clock.millis(),
                                                                "CloudWatchMetrics",
                                                                java.util.List.of(
                                                                        Map.of(
                                                                                "Namespace",
                                                                                "Snowthing/Images",
                                                                                "Dimensions",
                                                                                java.util.List.of(
                                                                                        java.util
                                                                                                .List
                                                                                                .of(
                                                                                                        "Action")),
                                                                                "Metrics",
                                                                                java.util.List.of(
                                                                                        Map.of(
                                                                                                "Name",
                                                                                                "GlobalBudgetRejected",
                                                                                                "Unit",
                                                                                                "Count"))))),
                                                        "Action",
                                                        action.name(),
                                                        "GlobalBudgetRejected",
                                                        count)));
                    } catch (JsonProcessingException failure) {
                        counter.addAndGet(count);
                        LoggerFactory.getLogger(getClass())
                                .warn("Image budget metric serialization failed", failure);
                    }
                });
    }
}
