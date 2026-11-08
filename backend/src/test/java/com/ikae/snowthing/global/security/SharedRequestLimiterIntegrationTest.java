package com.ikae.snowthing.global.security;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
class SharedRequestLimiterIntegrationTest {
    @Autowired SharedRequestLimiter limiter;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private String prefix;

    @BeforeEach
    void setup() {
        prefix = "budget-test-" + UUID.randomUUID();
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS request_budget_window (budget_key VARCHAR(100) PRIMARY KEY,used_count INT NOT NULL,expires_at_millis BIGINT NOT NULL)");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM request_budget_window WHERE budget_key LIKE ?", prefix + "%");
    }

    private List<SharedRequestLimiter.Limit> limits() {
        return List.of(
                new SharedRequestLimiter.Limit(prefix + ":hour", 5, Duration.ofHours(1)),
                new SharedRequestLimiter.Limit(prefix + ":day", 12, Duration.ofDays(1)));
    }

    private long concurrent(int count) throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks =
                    java.util.stream.IntStream.range(0, count)
                            .mapToObj(
                                    i ->
                                            (Callable<Boolean>)
                                                    () -> limiter.acquire(limits()).allowed())
                            .toList();
            long allowed = 0;
            for (var result : executor.invokeAll(tasks)) if (result.get()) allowed++;
            return allowed;
        }
    }

    @Test
    void concurrentNodesShareAtomicWindowsAndRejectsDoNotSpendDailyBudget() throws Exception {
        assertThat(concurrent(20)).isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT used_count FROM request_budget_window WHERE budget_key=?",
                                Integer.class,
                                prefix + ":day"))
                .isEqualTo(5);
        var replacement = new SharedRequestLimiter(jdbc, manager);
        assertThat(replacement.acquire(limits()).retryAfterSeconds()).isPositive();
        jdbc.update(
                "UPDATE request_budget_window SET expires_at_millis=0 WHERE budget_key=?",
                prefix + ":hour");
        assertThat(concurrent(10)).isEqualTo(5);
        jdbc.update(
                "UPDATE request_budget_window SET expires_at_millis=0 WHERE budget_key=?",
                prefix + ":hour");
        assertThat(concurrent(10)).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT used_count FROM request_budget_window WHERE budget_key=?",
                                Integer.class,
                                prefix + ":day"))
                .isEqualTo(12);
    }

    @Test
    void globalDenialIsReportedWithoutSpendingAccountBudget() {
        var budgets =
                List.of(
                        new SharedRequestLimiter.Limit(
                                prefix + ":account", 10, Duration.ofHours(1)),
                        new SharedRequestLimiter.Limit(
                                prefix + ":global:hour", 1, Duration.ofHours(1)));
        assertThat(limiter.acquire(budgets).allowed()).isTrue();
        var denied = limiter.acquire(budgets);
        assertThat(denied.deniedKey()).isEqualTo(prefix + ":global:hour");
        assertThat(denied.retryAfterSeconds()).isPositive();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT used_count FROM request_budget_window WHERE budget_key=?",
                                Integer.class,
                                prefix + ":account"))
                .isEqualTo(1);
    }

    @Test
    void unavailableStoreCannotEnableUnlimitedFallback() {
        var unavailable = org.mockito.Mockito.mock(JdbcTemplate.class);
        org.mockito.Mockito.when(
                        unavailable.queryForObject(
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.eq(Long.class)))
                .thenThrow(
                        new org.springframework.dao.DataAccessResourceFailureException("fixture"));
        assertThat(new SharedRequestLimiter(unavailable, manager).acquire(limits()).available())
                .isFalse();
    }

    @Test
    void cleanupRemovesMoreThanOneBatchOfExpiredWindows() {
        jdbc.batchUpdate(
                "INSERT INTO request_budget_window(budget_key,used_count,expires_at_millis) VALUES(?,1,1)",
                java.util.stream.IntStream.range(0, 1205)
                        .mapToObj(i -> new Object[] {"test:cleanup:" + i})
                        .toList());
        jdbc.update(
                "INSERT INTO request_budget_window(budget_key,used_count,expires_at_millis) VALUES('test:cleanup:alive',1,?)",
                System.currentTimeMillis() + 3_600_000L);

        limiter.removeExpiredWindows();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM request_budget_window WHERE budget_key LIKE 'test:cleanup:%'",
                                Integer.class))
                .isEqualTo(1);
        jdbc.update("DELETE FROM request_budget_window WHERE budget_key LIKE 'test:cleanup:%'");
    }
}
