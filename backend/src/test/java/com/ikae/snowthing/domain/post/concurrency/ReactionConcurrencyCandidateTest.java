package com.ikae.snowthing.domain.post.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("benchmark")
@SpringBootTest
@ActiveProfiles({"test", "concurrency"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReactionConcurrencyCandidateTest {

    private static final int CONCURRENT_REQUESTS = 100;
    private static final int MEASUREMENT_REPETITIONS = 3;
    private static final int MAX_OPTIMISTIC_RETRIES = 200;
    private static final int EXPECTED_POST_ID = 1;
    private static final int INITIAL_REACTION_COUNT = 0;
    private static final long INITIAL_VERSION = 0L;
    private static final int SINGLE_ROW_AFFECTED = 1;
    private static final int REPETITION_DISPLAY_OFFSET = 1;
    private static final long AWAIT_TIMEOUT_SECONDS = 60L;
    private static final String EXPERIMENT_POST_TABLE = "reaction_experiment_post";
    private static final String EXPERIMENT_VOTE_TABLE = "reaction_experiment_vote";
    private static final String SAFE_SCHEMA_MARKER = "test";
    private static final String SAFE_HOST_MARKER = "localhost";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate requiresNewTransaction;

    @BeforeAll
    void createExperimentTables() throws SQLException {
        verifyIsolatedLocalDatabase();
        requiresNewTransaction = new TransactionTemplate(transactionManager);
        requiresNewTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNewTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + EXPERIMENT_VOTE_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + EXPERIMENT_POST_TABLE);
        jdbcTemplate.execute(
                "CREATE TABLE "
                        + EXPERIMENT_POST_TABLE
                        + " (post_id BIGINT PRIMARY KEY, reaction_count INT NOT NULL, version BIGINT NOT NULL, CONSTRAINT chk_experiment_reaction_count CHECK (reaction_count >= 0)) ENGINE=InnoDB");
        jdbcTemplate.execute(
                "CREATE TABLE "
                        + EXPERIMENT_VOTE_TABLE
                        + " (vote_id BIGINT AUTO_INCREMENT PRIMARY KEY, post_id BIGINT NOT NULL, member_id BIGINT NOT NULL, CONSTRAINT uk_experiment_post_member UNIQUE (post_id, member_id)) ENGINE=InnoDB");
    }

    @AfterAll
    void dropExperimentTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + EXPERIMENT_VOTE_TABLE);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + EXPERIMENT_POST_TABLE);
    }

    @Test
    @DisplayName("Read-Modify-Write는 100명의 동시 추천에서 Lost Update를 반복 재현한다")
    void readModifyWrite_reproducesLostUpdate() throws Exception {
        List<CandidateResult> results = new ArrayList<>();

        for (int repetition = 0; repetition < MEASUREMENT_REPETITIONS; repetition++) {
            resetExperimentData();
            CandidateResult result = runReadModifyWrite();
            results.add(result);

            assertThat(result.rowCount()).isEqualTo(CONCURRENT_REQUESTS);
            assertThat(result.reactionCount()).isLessThan(result.rowCount());
            assertThat(result.errorCount()).isZero();
        }

        printResults("LOST_UPDATE", results);
    }

    @Test
    @DisplayName("원자적 UPDATE, 낙관적 락, 비관적 락은 같은 조건에서 추천 불변식을 지킨다")
    void candidates_preserveReactionInvariant() throws Exception {
        Map<Candidate, List<CandidateResult>> results = new EnumMap<>(Candidate.class);

        for (Candidate candidate : Candidate.values()) {
            List<CandidateResult> candidateResults = new ArrayList<>();
            for (int repetition = 0; repetition < MEASUREMENT_REPETITIONS; repetition++) {
                resetExperimentData();
                CandidateResult result = runCandidate(candidate);
                candidateResults.add(result);

                assertThat(result.rowCount()).isEqualTo(CONCURRENT_REQUESTS);
                assertThat(result.reactionCount()).isEqualTo(result.rowCount());
                assertThat(result.errorCount()).isZero();
            }
            results.put(candidate, List.copyOf(candidateResults));
            printResults(candidate.name(), candidateResults);
        }

        assertThat(results).containsOnlyKeys(Candidate.values());
    }

    private CandidateResult runReadModifyWrite() throws Exception {
        CountDownLatch afterReadBarrier = new CountDownLatch(CONCURRENT_REQUESTS);
        return executeConcurrent(
                memberId ->
                        requiresNewTransaction.executeWithoutResult(
                                ignored -> {
                                    int currentCount = currentReactionCount();
                                    awaitBarrier(afterReadBarrier);
                                    jdbcTemplate.update(
                                            "UPDATE "
                                                    + EXPERIMENT_POST_TABLE
                                                    + " SET reaction_count = ? WHERE post_id = ?",
                                            currentCount + 1,
                                            EXPECTED_POST_ID);
                                    jdbcTemplate.update(
                                            "INSERT INTO "
                                                    + EXPERIMENT_VOTE_TABLE
                                                    + " (post_id, member_id) VALUES (?, ?)",
                                            EXPECTED_POST_ID,
                                            memberId);
                                }),
                new AtomicInteger());
    }

    private CandidateResult runCandidate(Candidate candidate) throws Exception {
        AtomicInteger retryCount = new AtomicInteger();
        ConcurrentCommand command =
                switch (candidate) {
                    case ATOMIC_UPDATE -> this::applyWithAtomicUpdate;
                    case OPTIMISTIC_LOCK ->
                            memberId -> applyWithOptimisticLock(memberId, retryCount);
                    case PESSIMISTIC_LOCK -> this::applyWithPessimisticLock;
                };
        return executeConcurrent(command, retryCount);
    }

    private void applyWithAtomicUpdate(long memberId) {
        requiresNewTransaction.executeWithoutResult(
                ignored -> {
                    jdbcTemplate.update(
                            "UPDATE "
                                    + EXPERIMENT_POST_TABLE
                                    + " SET reaction_count = reaction_count + 1 WHERE post_id = ?",
                            EXPECTED_POST_ID);
                    int inserted = insertVoteIfAbsent(memberId);
                    if (inserted != SINGLE_ROW_AFFECTED) {
                        jdbcTemplate.update(
                                "UPDATE "
                                        + EXPERIMENT_POST_TABLE
                                        + " SET reaction_count = reaction_count - 1 WHERE post_id = ?",
                                EXPECTED_POST_ID);
                    }
                });
    }

    private void applyWithOptimisticLock(long memberId, AtomicInteger retryCount) {
        for (int attempt = 0; attempt <= MAX_OPTIMISTIC_RETRIES; attempt++) {
            try {
                requiresNewTransaction.executeWithoutResult(
                        ignored -> {
                            PostSnapshot snapshot = currentSnapshot();
                            int updated =
                                    jdbcTemplate.update(
                                            "UPDATE "
                                                    + EXPERIMENT_POST_TABLE
                                                    + " SET reaction_count = ?, version = version + 1 WHERE post_id = ? AND version = ?",
                                            snapshot.reactionCount() + 1,
                                            EXPECTED_POST_ID,
                                            snapshot.version());
                            if (updated == INITIAL_REACTION_COUNT) {
                                throw new OptimisticConflictException();
                            }
                            int inserted = insertVoteIfAbsent(memberId);
                            if (inserted == INITIAL_REACTION_COUNT) {
                                throw new IllegalStateException(
                                        "낙관적 락 실험에서 예상하지 않은 중복 추천이 발생했습니다.");
                            }
                        });
                return;
            } catch (OptimisticConflictException conflict) {
                retryCount.incrementAndGet();
                Thread.onSpinWait();
            }
        }
        throw new IllegalStateException("낙관적 락 재시도 한도를 초과했습니다.");
    }

    private void applyWithPessimisticLock(long memberId) {
        requiresNewTransaction.executeWithoutResult(
                ignored -> {
                    jdbcTemplate.queryForObject(
                            "SELECT reaction_count FROM "
                                    + EXPERIMENT_POST_TABLE
                                    + " WHERE post_id = ? FOR UPDATE",
                            Integer.class,
                            EXPECTED_POST_ID);
                    int inserted = insertVoteIfAbsent(memberId);
                    if (inserted == SINGLE_ROW_AFFECTED) {
                        jdbcTemplate.update(
                                "UPDATE "
                                        + EXPERIMENT_POST_TABLE
                                        + " SET reaction_count = reaction_count + 1 WHERE post_id = ?",
                                EXPECTED_POST_ID);
                    }
                });
    }

    private CandidateResult executeConcurrent(ConcurrentCommand command, AtomicInteger retryCount)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyBarrier = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startBarrier = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger errors = new AtomicInteger();

        for (long memberId = 1; memberId <= CONCURRENT_REQUESTS; memberId++) {
            long currentMemberId = memberId;
            futures.add(
                    executor.submit(
                            () -> {
                                readyBarrier.countDown();
                                awaitLatch(startBarrier);
                                try {
                                    command.execute(currentMemberId);
                                } catch (RuntimeException exception) {
                                    errors.incrementAndGet();
                                    throw exception;
                                }
                            }));
        }

        assertThat(readyBarrier.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        long startedAt = System.nanoTime();
        startBarrier.countDown();
        try {
            for (Future<?> future : futures) {
                future.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return new CandidateResult(
                currentReactionCount(),
                currentVoteCount(),
                errors.get(),
                retryCount.get(),
                elapsedMillis);
    }

    private int insertVoteIfAbsent(long memberId) {
        return jdbcTemplate.update(
                "INSERT IGNORE INTO "
                        + EXPERIMENT_VOTE_TABLE
                        + " (post_id, member_id) VALUES (?, ?)",
                EXPECTED_POST_ID,
                memberId);
    }

    private void resetExperimentData() {
        jdbcTemplate.update("DELETE FROM " + EXPERIMENT_VOTE_TABLE);
        jdbcTemplate.update("DELETE FROM " + EXPERIMENT_POST_TABLE);
        jdbcTemplate.update(
                "INSERT INTO "
                        + EXPERIMENT_POST_TABLE
                        + " (post_id, reaction_count, version) VALUES (?, ?, ?)",
                EXPECTED_POST_ID,
                INITIAL_REACTION_COUNT,
                INITIAL_VERSION);
    }

    private int currentReactionCount() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT reaction_count FROM "
                                + EXPERIMENT_POST_TABLE
                                + " WHERE post_id = ?",
                        Integer.class,
                        EXPECTED_POST_ID);
        return count == null ? INITIAL_REACTION_COUNT : count;
    }

    private int currentVoteCount() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + EXPERIMENT_VOTE_TABLE, Integer.class);
        return count == null ? INITIAL_REACTION_COUNT : count;
    }

    private PostSnapshot currentSnapshot() {
        return jdbcTemplate.queryForObject(
                "SELECT reaction_count, version FROM "
                        + EXPERIMENT_POST_TABLE
                        + " WHERE post_id = ?",
                (resultSet, rowNumber) ->
                        new PostSnapshot(
                                resultSet.getInt("reaction_count"), resultSet.getLong("version")),
                EXPECTED_POST_ID);
    }

    private void awaitBarrier(CountDownLatch barrier) {
        barrier.countDown();
        awaitLatch(barrier);
    }

    private void awaitLatch(CountDownLatch barrier) {
        try {
            if (!barrier.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 장벽 대기 시간이 초과됐습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 장벽 대기가 중단됐습니다.", exception);
        }
    }

    private void verifyIsolatedLocalDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String url = metadata.getURL();
            String catalog = connection.getCatalog();
            assertThat(url).contains(SAFE_HOST_MARKER);
            assertThat(catalog).containsIgnoringCase(SAFE_SCHEMA_MARKER);
        }
    }

    private void printResults(String candidate, List<CandidateResult> results) {
        for (int repetition = 0; repetition < results.size(); repetition++) {
            CandidateResult result = results.get(repetition);
            System.out.printf(
                    "REACTION_CONCURRENCY candidate=%s repetition=%d requests=%d count=%d rows=%d errors=%d retries=%d elapsedMs=%d%n",
                    candidate,
                    repetition + REPETITION_DISPLAY_OFFSET,
                    CONCURRENT_REQUESTS,
                    result.reactionCount(),
                    result.rowCount(),
                    result.errorCount(),
                    result.retryCount(),
                    result.elapsedMillis());
        }
    }

    private enum Candidate {
        ATOMIC_UPDATE,
        OPTIMISTIC_LOCK,
        PESSIMISTIC_LOCK
    }

    @FunctionalInterface
    private interface ConcurrentCommand {
        void execute(long memberId);
    }

    private record CandidateResult(
            int reactionCount, int rowCount, int errorCount, int retryCount, long elapsedMillis) {}

    private record PostSnapshot(int reactionCount, long version) {}

    private static final class OptimisticConflictException extends RuntimeException {}
}
