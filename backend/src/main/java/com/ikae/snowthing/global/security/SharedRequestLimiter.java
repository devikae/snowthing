package com.ikae.snowthing.global.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SharedRequestLimiter {
    public record Limit(String key, int count, Duration duration) {}

    public record Decision(boolean available, long retryAfterSeconds, String deniedKey) {
        public Decision(boolean available, long retryAfterSeconds) {
            this(available, retryAfterSeconds, null);
        }

        public boolean allowed() {
            return available && retryAfterSeconds == 0;
        }
    }

    private record Window(int count, long expires) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public SharedRequestLimiter(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private long now() {
        return jdbc.queryForObject(
                "SELECT FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))*1000)", Long.class);
    }

    public Decision acquire(List<Limit> requested) {
        var limits = new ArrayList<>(List.copyOf(requested));
        limits.sort(Comparator.comparing(Limit::key));
        try {
            Decision result =
                    transactions.execute(
                            status -> {
                                long initial = now();
                                var windows = new ArrayList<Window>();
                                // Lock every row in stable order; no window is spent until all
                                // permit.
                                for (var limit : limits) {
                                    jdbc.update(
                                            "INSERT INTO request_budget_window(budget_key,used_count,expires_at_millis) VALUES(?,0,?) ON DUPLICATE KEY UPDATE budget_key=budget_key",
                                            limit.key(),
                                            initial + limit.duration().toMillis());
                                    windows.add(
                                            jdbc.queryForObject(
                                                    "SELECT used_count,expires_at_millis FROM request_budget_window WHERE budget_key=? FOR UPDATE",
                                                    (row, index) ->
                                                            new Window(
                                                                    row.getInt(1), row.getLong(2)),
                                                    limit.key()));
                                }
                                long current = now(), retry = 0;
                                String deniedKey = null;
                                for (int i = 0; i < limits.size(); i++) {
                                    var window = windows.get(i);
                                    if (window.expires() > current
                                            && window.count() >= limits.get(i).count()) {
                                        // Prefer the global reason when both account and global
                                        // caps are full.
                                        if (deniedKey == null
                                                || limits.get(i).key().contains(":global:"))
                                            deniedKey = limits.get(i).key();
                                        retry =
                                                Math.max(
                                                        retry,
                                                        Math.max(
                                                                1,
                                                                (window.expires() - current + 999)
                                                                        / 1000));
                                    }
                                }
                                if (retry > 0) return new Decision(true, retry, deniedKey);
                                for (int i = 0; i < limits.size(); i++) {
                                    var window = windows.get(i);
                                    var limit = limits.get(i);
                                    boolean expired = window.expires() <= current;
                                    jdbc.update(
                                            "UPDATE request_budget_window SET used_count=?,expires_at_millis=? WHERE budget_key=?",
                                            expired ? 1 : window.count() + 1,
                                            expired
                                                    ? current + limit.duration().toMillis()
                                                    : window.expires(),
                                            limit.key());
                                }
                                return new Decision(true, 0);
                            });
            return result == null ? new Decision(false, 60) : result;
        } catch (DataAccessException | TransactionException exception) {
            return new Decision(false, 60); // No unbounded process-local fallback.
        }
    }

    @Scheduled(
            initialDelayString = "${snowthing.request-budget.cleanup-delay-ms:3600000}",
            fixedDelayString = "${snowthing.request-budget.cleanup-delay-ms:3600000}")
    public void removeExpiredWindows() {
        jdbc.update(
                "DELETE FROM request_budget_window WHERE expires_at_millis < FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))*1000) ORDER BY budget_key LIMIT 500");
    }
}
