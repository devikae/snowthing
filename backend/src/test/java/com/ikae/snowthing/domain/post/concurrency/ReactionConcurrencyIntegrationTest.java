package com.ikae.snowthing.domain.post.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.ActiveProfiles;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.ReactionCountMismatch;
import com.ikae.snowthing.domain.post.dto.ReactionReconciliationResult;
import com.ikae.snowthing.domain.post.dto.ReactionResponse;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostReaction;
import com.ikae.snowthing.domain.post.entity.ReactionType;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostReactionRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.ReactionService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag("benchmark")
@SpringBootTest
@ActiveProfiles({"test", "concurrency"})
class ReactionConcurrencyIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 100;
    private static final int MIXED_COMMAND_COUNT = 50;
    private static final int REPEAT_COUNT = 3;
    private static final int EXPECTED_SINGLE_REACTION = 1;
    private static final int EXPECTED_NO_REACTION = 0;
    private static final int INVALID_NEGATIVE_COUNT = -1;
    private static final int FORCED_INCONSISTENT_COUNT = 7;
    private static final long AWAIT_TIMEOUT_SECONDS = 60L;
    private static final String CATEGORY_CODE = "REACTION_CONCURRENCY";
    private static final String CATEGORY_NAME = "추천 동시성 테스트";
    private static final String POST_TITLE = "추천 동시성 테스트 게시글";
    private static final String POST_CONTENT = "추천 동시성 테스트 본문";
    private static final String DEFAULT_CLIENT_IP = "127.0.0.1";
    private static final String ANONYMOUS_VOTER_ID = "reaction-test-anonymous-voter";
    private static final String MEMBER_EMAIL_FORMAT = "reaction-user-%03d@example.com";
    private static final String MEMBER_NICKNAME_FORMAT = "추천테스트%03d";
    private static final String MEMBER_PUBLIC_ID_FORMAT = "reaction-test-member-%03d";
    private static final String PASSWORD_PLACEHOLDER = "encoded-password";
    private static final String INSERT_FAILURE_TRIGGER = "trg_reaction_insert_failure";
    private static final String COUNTER_FAILURE_TRIGGER = "trg_reaction_counter_failure";
    private static final String TRIGGER_FAILURE_MESSAGE = "forced reaction test failure";
    private static final String START_BARRIER_TIMEOUT_MESSAGE = "동시성 테스트 시작 장벽 대기 시간이 초과됐습니다.";
    private static final String START_BARRIER_INTERRUPTED_MESSAGE = "동시성 테스트 시작 장벽 대기가 중단됐습니다.";
    private static final String UPDATE_LIKE_COUNT_SQL =
            "UPDATE post SET like_count = ? WHERE post_id = ?";

    @Autowired private ReactionService reactionService;
    @Autowired private PostRepository postRepository;
    @Autowired private PostReactionRepository reactionRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Post post;
    private List<Member> members;
    private List<CustomUserDetails> users;

    @BeforeEach
    void setUp() {
        dropFailureTriggers();
        reactionRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        PostCategory category =
                categoryRepository.save(
                        PostCategory.builder().code(CATEGORY_CODE).name(CATEGORY_NAME).build());
        members = memberRepository.saveAll(createMembers());
        users = members.stream().map(CustomUserDetails::new).toList();
        post =
                postRepository.save(
                        Post.builder()
                                .member(members.getFirst())
                                .category(category)
                                .title(POST_TITLE)
                                .content(POST_CONTENT)
                                .writerIp(DEFAULT_CLIENT_IP)
                                .isAnonymous(false)
                                .build());
    }

    @AfterEach
    void tearDown() {
        dropFailureTriggers();
    }

    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("서로 다른 100명이 동시에 추천해도 row 수와 likeCount는 100으로 일치한다")
    void differentMembers_concurrentPut_preservesInvariant() throws Exception {
        ConcurrentResult result =
                executeConcurrent(
                        users,
                        user ->
                                reactionService.apply(
                                        post.getPublicId(),
                                        ReactionType.LIKE,
                                        user,
                                        DEFAULT_CLIENT_IP,
                                        null));

        assertThat(result.errors()).isZero();
        assertThat(result.successes()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(result.changed()).isEqualTo(CONCURRENT_REQUESTS);
        assertInvariant(CONCURRENT_REQUESTS);
    }

    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("동일 사용자의 PUT 100건은 추천 row와 likeCount를 1로 유지한다")
    void sameMember_concurrentPut_isIdempotent() throws Exception {
        CustomUserDetails sameUser = users.getFirst();
        List<CustomUserDetails> repeatedUsers =
                java.util.Collections.nCopies(CONCURRENT_REQUESTS, sameUser);

        ConcurrentResult result =
                executeConcurrent(
                        repeatedUsers,
                        user ->
                                reactionService.apply(
                                        post.getPublicId(),
                                        ReactionType.LIKE,
                                        user,
                                        DEFAULT_CLIENT_IP,
                                        null));

        assertThat(result.errors()).isZero();
        assertThat(result.successes()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(result.changed()).isEqualTo(EXPECTED_SINGLE_REACTION);
        assertInvariant(EXPECTED_SINGLE_REACTION);
    }

    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("동일 사용자의 DELETE 100건은 최종 상태를 미추천으로 유지한다")
    void sameMember_concurrentDelete_isIdempotent() throws Exception {
        CustomUserDetails sameUser = users.getFirst();
        reactionService.apply(
                post.getPublicId(), ReactionType.LIKE, sameUser, DEFAULT_CLIENT_IP, null);
        List<CustomUserDetails> repeatedUsers =
                java.util.Collections.nCopies(CONCURRENT_REQUESTS, sameUser);

        ConcurrentResult result =
                executeConcurrent(
                        repeatedUsers,
                        user ->
                                reactionService.remove(
                                        post.getPublicId(),
                                        ReactionType.LIKE,
                                        user,
                                        DEFAULT_CLIENT_IP,
                                        null));

        assertThat(result.errors()).isZero();
        assertThat(result.successes()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(result.changed()).isEqualTo(EXPECTED_SINGLE_REACTION);
        assertInvariant(EXPECTED_NO_REACTION);
    }

    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("동일 사용자의 추천과 취소가 동시에 발생해도 row 수와 likeCount는 일치한다")
    void sameMember_mixedPutAndDelete_preservesInvariant() throws Exception {
        CustomUserDetails sameUser = users.getFirst();
        List<ReactionCommand> commands = new ArrayList<>();
        for (int index = 0; index < MIXED_COMMAND_COUNT; index++) {
            commands.add(
                    () ->
                            reactionService.apply(
                                    post.getPublicId(),
                                    ReactionType.LIKE,
                                    sameUser,
                                    DEFAULT_CLIENT_IP,
                                    null));
            commands.add(
                    () ->
                            reactionService.remove(
                                    post.getPublicId(),
                                    ReactionType.LIKE,
                                    sameUser,
                                    DEFAULT_CLIENT_IP,
                                    null));
        }

        ConcurrentResult result = executeConcurrentCommands(commands);

        assertThat(result.errors()).isZero();
        assertThat(result.successes()).isEqualTo(CONCURRENT_REQUESTS);
        int rowCount = activeReactionRowCount();
        assertThat(rowCount).isBetween(EXPECTED_NO_REACTION, EXPECTED_SINGLE_REACTION);
        assertInvariant(rowCount);
    }

    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("동일 익명 사용자의 PUT 100건은 추천 row와 likeCount를 1로 유지한다")
    void sameAnonymousVoter_concurrentPut_isIdempotent() throws Exception {
        ConcurrentResult result =
                executeConcurrentCommands(
                        repeatedAnonymousCommands(
                                () ->
                                        reactionService.apply(
                                                post.getPublicId(),
                                                ReactionType.LIKE,
                                                null,
                                                DEFAULT_CLIENT_IP,
                                                ANONYMOUS_VOTER_ID)));

        assertThat(result.errors()).isZero();
        assertThat(result.successes()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(result.changed()).isEqualTo(EXPECTED_SINGLE_REACTION);
        assertInvariant(EXPECTED_SINGLE_REACTION);
    }

    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("동일 익명 사용자의 DELETE 100건은 최종 상태를 미추천으로 유지한다")
    void sameAnonymousVoter_concurrentDelete_isIdempotent() throws Exception {
        reactionService.apply(
                post.getPublicId(),
                ReactionType.LIKE,
                null,
                DEFAULT_CLIENT_IP,
                ANONYMOUS_VOTER_ID);

        ConcurrentResult result =
                executeConcurrentCommands(
                        repeatedAnonymousCommands(
                                () ->
                                        reactionService.remove(
                                                post.getPublicId(),
                                                ReactionType.LIKE,
                                                null,
                                                DEFAULT_CLIENT_IP,
                                                ANONYMOUS_VOTER_ID)));

        assertThat(result.errors()).isZero();
        assertThat(result.successes()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(result.changed()).isEqualTo(EXPECTED_SINGLE_REACTION);
        assertInvariant(EXPECTED_NO_REACTION);
    }

    @RepeatedTest(REPEAT_COUNT)
    @DisplayName("동일 익명 사용자의 추천과 취소가 동시에 발생해도 row 수와 likeCount는 일치한다")
    void sameAnonymousVoter_mixedPutAndDelete_preservesInvariant() throws Exception {
        List<ReactionCommand> commands = new ArrayList<>();
        for (int index = 0; index < MIXED_COMMAND_COUNT; index++) {
            commands.add(
                    () ->
                            reactionService.apply(
                                    post.getPublicId(),
                                    ReactionType.LIKE,
                                    null,
                                    DEFAULT_CLIENT_IP,
                                    ANONYMOUS_VOTER_ID));
            commands.add(
                    () ->
                            reactionService.remove(
                                    post.getPublicId(),
                                    ReactionType.LIKE,
                                    null,
                                    DEFAULT_CLIENT_IP,
                                    ANONYMOUS_VOTER_ID));
        }

        ConcurrentResult result = executeConcurrentCommands(commands);

        assertThat(result.errors()).isZero();
        assertThat(result.successes()).isEqualTo(CONCURRENT_REQUESTS);
        int rowCount = activeReactionRowCount();
        assertThat(rowCount).isBetween(EXPECTED_NO_REACTION, EXPECTED_SINGLE_REACTION);
        assertInvariant(rowCount);
    }

    @Test
    @DisplayName("추천 row INSERT가 실패하면 먼저 증가한 likeCount도 함께 롤백된다")
    void insertFailure_rollsBackCounter() {
        createInsertFailureTrigger();

        assertThatThrownBy(
                        () ->
                                reactionService.apply(
                                        post.getPublicId(),
                                        ReactionType.LIKE,
                                        users.getFirst(),
                                        DEFAULT_CLIENT_IP,
                                        null))
                .isInstanceOf(JpaSystemException.class);

        assertInvariant(EXPECTED_NO_REACTION);
    }

    @Test
    @DisplayName("카운터 UPDATE가 실패하면 삭제한 추천 row도 함께 롤백된다")
    void counterFailure_rollsBackDeletedReaction() {
        reactionService.apply(
                post.getPublicId(), ReactionType.LIKE, users.getFirst(), DEFAULT_CLIENT_IP, null);
        createCounterFailureTrigger();

        assertThatThrownBy(
                        () ->
                                reactionService.remove(
                                        post.getPublicId(),
                                        ReactionType.LIKE,
                                        users.getFirst(),
                                        DEFAULT_CLIENT_IP,
                                        null))
                .isInstanceOf(JpaSystemException.class);

        assertInvariant(EXPECTED_SINGLE_REACTION);
    }

    @Test
    @DisplayName("DB UNIQUE 제약조건은 같은 회원의 중복 추천 row를 차단한다")
    void uniqueConstraint_blocksDuplicateRows() {
        PostReaction first =
                PostReaction.builder()
                        .post(post)
                        .member(members.getFirst())
                        .writerIp(DEFAULT_CLIENT_IP)
                        .type(ReactionType.LIKE)
                        .build();
        PostReaction duplicate =
                PostReaction.builder()
                        .post(post)
                        .member(members.getFirst())
                        .writerIp(DEFAULT_CLIENT_IP)
                        .type(ReactionType.LIKE)
                        .build();
        reactionRepository.saveAndFlush(first);

        assertThatThrownBy(() -> reactionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB UNIQUE 제약조건은 같은 익명 식별자의 중복 추천 row를 차단한다")
    void uniqueConstraint_blocksDuplicateAnonymousRows() {
        PostReaction first = anonymousReaction();
        PostReaction duplicate = anonymousReaction();
        reactionRepository.saveAndFlush(first);

        assertThatThrownBy(() -> reactionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB CHECK 제약조건은 likeCount 음수 저장을 차단한다")
    void checkConstraint_blocksNegativeLikeCount() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        UPDATE_LIKE_COUNT_SQL,
                                        INVALID_NEGATIVE_COUNT,
                                        post.getId()))
                .isInstanceOf(DataAccessException.class);

        assertInvariant(EXPECTED_NO_REACTION);
    }

    @Test
    @DisplayName("Reconciliation은 불일치 게시글을 탐지하고 활성 추천 row 수로 복구한다")
    void reconciliation_detectsAndRepairsMismatch() {
        reactionService.apply(
                post.getPublicId(), ReactionType.LIKE, users.getFirst(), DEFAULT_CLIENT_IP, null);
        jdbcTemplate.update(
                UPDATE_LIKE_COUNT_SQL, FORCED_INCONSISTENT_COUNT, post.getId());

        List<ReactionCountMismatch> mismatches =
                reactionService.findCountMismatches(ReactionType.LIKE);
        assertThat(mismatches)
                .anySatisfy(
                        mismatch -> {
                            assertThat(mismatch.postPublicId()).isEqualTo(post.getPublicId());
                            assertThat(mismatch.storedCount()).isEqualTo(FORCED_INCONSISTENT_COUNT);
                            assertThat(mismatch.actualCount()).isEqualTo(EXPECTED_SINGLE_REACTION);
                        });

        ReactionReconciliationResult result =
                reactionService.reconcile(post.getPublicId(), ReactionType.LIKE);

        assertThat(result.beforeCount()).isEqualTo(FORCED_INCONSISTENT_COUNT);
        assertThat(result.afterCount()).isEqualTo(EXPECTED_SINGLE_REACTION);
        assertThat(result.changed()).isTrue();
        assertInvariant(EXPECTED_SINGLE_REACTION);
    }

    private List<Member> createMembers() {
        List<Member> createdMembers = new ArrayList<>();
        for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
            createdMembers.add(
                    Member.builder()
                            .publicId(MEMBER_PUBLIC_ID_FORMAT.formatted(index))
                            .email(MEMBER_EMAIL_FORMAT.formatted(index))
                            .password(PASSWORD_PLACEHOLDER)
                            .nickname(MEMBER_NICKNAME_FORMAT.formatted(index))
                            .role(Role.ROLE_USER)
                            .build());
        }
        return createdMembers;
    }

    private List<ReactionCommand> repeatedAnonymousCommands(ReactionCommand command) {
        return java.util.Collections.nCopies(CONCURRENT_REQUESTS, command);
    }

    private PostReaction anonymousReaction() {
        return PostReaction.builder()
                .post(post)
                .writerIp(DEFAULT_CLIENT_IP)
                .anonymousVoterId(ANONYMOUS_VOTER_ID)
                .type(ReactionType.LIKE)
                .build();
    }

    private ConcurrentResult executeConcurrent(
            List<CustomUserDetails> requestUsers, ReactionUserCommand command) throws Exception {
        List<ReactionCommand> commands =
                requestUsers.stream()
                        .<ReactionCommand>map(user -> () -> command.execute(user))
                        .toList();
        return executeConcurrentCommands(commands);
    }

    private ConcurrentResult executeConcurrentCommands(List<ReactionCommand> commands)
            throws Exception {
        long startedAt = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(commands.size());
        CountDownLatch readyBarrier = new CountDownLatch(commands.size());
        CountDownLatch startBarrier = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger changed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (ReactionCommand command : commands) {
            futures.add(
                    executor.submit(
                            () -> {
                                readyBarrier.countDown();
                                await(startBarrier);
                                try {
                                    ReactionResponse response = command.execute();
                                    if (response.changed()) {
                                        changed.incrementAndGet();
                                    }
                                } catch (RuntimeException exception) {
                                    errors.incrementAndGet();
                                    throw exception;
                                }
                            }));
        }

        assertThat(readyBarrier.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
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
        ConcurrentResult result =
                new ConcurrentResult(
                        changed.get(), commands.size() - errors.get(), errors.get(), elapsedMillis);
        log.info(
                "추천 동시성 검증 - requests: {}, successes: {}, errors: {}, changed: {}, elapsedMs: {}",
                commands.size(),
                result.successes(),
                result.errors(),
                result.changed(),
                result.elapsedMillis());
        return result;
    }

    private void assertInvariant(int expectedCount) {
        Integer storedCount =
                jdbcTemplate.queryForObject(
                        "SELECT like_count FROM post WHERE post_id = ?",
                        Integer.class,
                        post.getId());
        assertThat(storedCount).isEqualTo(expectedCount);
        assertThat(activeReactionRowCount()).isEqualTo(expectedCount);
        assertThat(storedCount).isNotNegative();
    }

    private int activeReactionRowCount() {
        return Math.toIntExact(
                reactionRepository.countByPostIdAndType(post.getId(), ReactionType.LIKE));
    }

    private void createInsertFailureTrigger() {
        jdbcTemplate.execute(
                "CREATE TRIGGER "
                        + INSERT_FAILURE_TRIGGER
                        + " BEFORE INSERT ON post_reaction FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '"
                        + TRIGGER_FAILURE_MESSAGE
                        + "'");
    }

    private void createCounterFailureTrigger() {
        jdbcTemplate.execute(
                "CREATE TRIGGER "
                        + COUNTER_FAILURE_TRIGGER
                        + " BEFORE UPDATE ON post FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '"
                        + TRIGGER_FAILURE_MESSAGE
                        + "'");
    }

    private void dropFailureTriggers() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + INSERT_FAILURE_TRIGGER);
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + COUNTER_FAILURE_TRIGGER);
    }

    private void await(CountDownLatch barrier) {
        try {
            if (!barrier.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(START_BARRIER_TIMEOUT_MESSAGE);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(START_BARRIER_INTERRUPTED_MESSAGE, exception);
        }
    }

    @FunctionalInterface
    private interface ReactionCommand {
        ReactionResponse execute();
    }

    @FunctionalInterface
    private interface ReactionUserCommand {
        ReactionResponse execute(CustomUserDetails user);
    }

    private record ConcurrentResult(int changed, int successes, int errors, long elapsedMillis) {}
}
