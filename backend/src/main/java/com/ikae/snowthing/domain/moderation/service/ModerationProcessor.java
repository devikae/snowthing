package com.ikae.snowthing.domain.moderation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.moderation.api.ModerationApiClient;
import com.ikae.snowthing.domain.moderation.config.ModerationProperties;
import com.ikae.snowthing.domain.moderation.entity.ModerationStage;
import com.ikae.snowthing.domain.moderation.entity.ModerationStatus;
import com.ikae.snowthing.domain.moderation.entity.ModerationVerdict;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository.Row;
import com.ikae.snowthing.domain.moderation.repository.ModerationSourceRepository;
import com.ikae.snowthing.domain.moderation.repository.ModerationSourceRepository.Source;
import com.ikae.snowthing.domain.moderation.service.ModerationRuleEvaluator.RuleResult;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * content_moderation 대기 줄을 묶어 판정한다 (IKA-80).
 *
 * <ul>
 *   <li>가져오기: 검색 색인 outbox와 같다. FOR UPDATE SKIP LOCKED로 고르고 임대(lease_until)를 걸며 시도 횟수를 미리 올린다. 처리 중
 *       서버가 죽으면 임대가 끝난 뒤 다시 가져간다. 기록과 재시도 예약은 가져간 시도 번호가 그대로일 때만 한다. 임대가 끝나 다른 처리기가 다시
 *       가져갔으면 늦게 돌아온 이전 시도는 아무것도 쓰지 않는다.
 *   <li>원문은 처리 시점에 다시 읽는다. 지워졌거나 이미 가려졌거나 같은 대상의 더 새 줄이 있으면 판정하지 않는다(SKIPPED). 판정을 기록할
 *       때(최종 실패 포함) 줄을 잠근 뒤 더 새 줄이 생겼는지 다시 보고, 생겼으면 이전 수정본의 판정을 검토 대기로 올리지 않고 SKIPPED로 닫는다.
 *   <li>1단계 규칙 뒤 2단계가 켜져 있으면 남은 글을 한 요청에 묶어 보낸다. 2단계 호출은 트랜잭션 밖에서 한다.
 *   <li>판정 기록과 숨김은 줄마다 트랜잭션 하나로 함께 커밋한다.
 *   <li>실패하면 다음 시각을 늦춰 다시 하고, 정해진 횟수만큼 실패하면 FAILED로 두고 검토 대기로 보낸다.
 *   <li>로그에는 본문을 남기지 않는다. 줄 ID, 대상, 판정, 걸린 규칙 이름만 남긴다.
 * </ul>
 */
@Slf4j
@Service
public class ModerationProcessor {

    public static final String API_FAILED = "API_FAILED";
    public static final String CHECK_FAILED = "CHECK_FAILED";
    static final int API_INPUT_MAX_LENGTH = 4000;
    private static final int MAX_REASONS_LENGTH = 1000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ContentModerationRepository repository;
    private final ModerationSourceRepository sources;
    private final ModerationRuleEvaluator rules;
    private final ModerationActions actions;
    private final ModerationApiClient apiClient;
    private final ModerationProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ModerationProcessor(
            ContentModerationRepository repository,
            ModerationSourceRepository sources,
            ModerationRuleEvaluator rules,
            ModerationActions actions,
            ModerationApiClient apiClient,
            ModerationProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.sources = sources;
        this.rules = rules;
        this.actions = actions;
        this.apiClient = apiClient;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** 한 묶음을 가져와 처리한다. 가져온 줄 수를 돌려준다(0이면 남은 줄이 없다). */
    public int processBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Row> rows =
                tx.execute(
                        status ->
                                repository.claim(
                                        now, now.plus(properties.lease()), properties.batchSize()));
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        List<ApiCandidate> apiCandidates = new ArrayList<>();
        boolean apiActive = properties.api().active();
        for (Row row : rows) {
            try {
                Optional<Source> source = sources.find(row.targetType(), row.targetId());
                if (source.isEmpty() || !source.get().visible()) {
                    skip(row, "NOT_VISIBLE");
                    continue;
                }
                if (repository.hasNewerRow(row.targetType(), row.targetId(), row.id())
                        || !row.contentHash()
                                .equals(
                                        ModerationTextRules.contentHash(
                                                source.get().title(), source.get().body()))) {
                    skip(row, "SUPERSEDED");
                    continue;
                }
                RuleResult result = rules.evaluate(row, source.get());
                if (apiActive && !result.skipApi()) {
                    apiCandidates.add(
                            new ApiCandidate(
                                    row,
                                    result,
                                    ModerationTextRules.apiInput(
                                            source.get().title(),
                                            source.get().body(),
                                            API_INPUT_MAX_LENGTH)));
                } else {
                    finish(row, result.verdict(), ModerationStage.RULE, result.codes(), Map.of());
                }
            } catch (RuntimeException failure) {
                handleFailure(row, null, failure);
            }
        }
        callApi(apiCandidates);
        return rows.size();
    }

    private void callApi(List<ApiCandidate> candidates) {
        int chunkSize = properties.api().maxInputsPerCall();
        for (int from = 0; from < candidates.size(); from += chunkSize) {
            List<ApiCandidate> chunk =
                    candidates.subList(from, Math.min(from + chunkSize, candidates.size()));
            List<Map<String, Double>> scores;
            try {
                scores = apiClient.moderate(chunk.stream().map(ApiCandidate::input).toList());
                if (scores.size() != chunk.size()) {
                    throw new IllegalStateException("moderation result size mismatch");
                }
            } catch (RuntimeException failure) {
                chunk.forEach(
                        candidate -> handleFailure(candidate.row(), candidate.rules(), failure));
                continue;
            }
            for (int i = 0; i < chunk.size(); i++) {
                ApiCandidate candidate = chunk.get(i);
                try {
                    ModerationScoreRules.Result judged =
                            ModerationScoreRules.judge(scores.get(i), properties.api());
                    finish(
                            candidate.row(),
                            candidate.rules().verdict().max(judged.verdict()),
                            ModerationStage.MODERATION_API,
                            candidate.rules().codes(),
                            judged.notableScores());
                } catch (RuntimeException failure) {
                    handleFailure(candidate.row(), candidate.rules(), failure);
                }
            }
        }
    }

    private void finish(
            Row row,
            ModerationVerdict verdict,
            ModerationStage stage,
            List<String> codes,
            Map<String, Double> scores) {
        LocalDateTime now = LocalDateTime.now(clock);
        String outcome =
                tx.execute(
                        status -> {
                            if (repository.lockPending(row).isEmpty()) {
                                return "ALREADY_DONE";
                            }
                            if (repository.hasNewerRow(row.targetType(), row.targetId(), row.id())) {
                                // 판정하는 동안 작성자가 고쳤다. 이전 수정본의 판정은 검토 대기열에 올리지 않고 새 줄이 따로 판정한다.
                                repository.complete(
                                        row,
                                        ModerationStatus.SKIPPED,
                                        stage,
                                        null,
                                        reasons(codes, scores, "SUPERSEDED"),
                                        now);
                                return "SUPERSEDED";
                            }
                            if (verdict == ModerationVerdict.HIDE
                                    && !actions.hide(
                                            row.targetType(),
                                            row.targetId(),
                                            row.id(),
                                            row.contentHash())) {
                                // 판정 사이에 지워졌거나 가려졌거나 고쳐졌다. 고쳤으면 새 줄이 따로 판정한다.
                                repository.complete(
                                        row,
                                        ModerationStatus.SKIPPED,
                                        stage,
                                        null,
                                        reasons(codes, scores, "NOT_VISIBLE"),
                                        now);
                                return "SKIPPED";
                            }
                            repository.complete(
                                    row,
                                    ModerationStatus.DONE,
                                    stage,
                                    verdict,
                                    reasons(codes, scores, null),
                                    now);
                            repository.supersedeOlder(
                                    row.targetType(), row.targetId(), row.id(), now);
                            return verdict.name();
                        });
        if (verdict != ModerationVerdict.PASS) {
            log.info(
                    "content moderation {}: id={}, target={}:{}, stage={}, rules={}, scores={}",
                    outcome,
                    row.id(),
                    row.targetType(),
                    row.targetId(),
                    stage,
                    codes,
                    scores.keySet());
        }
    }

    private void skip(Row row, String why) {
        LocalDateTime now = LocalDateTime.now(clock);
        tx.executeWithoutResult(
                status ->
                        repository.complete(
                                row,
                                ModerationStatus.SKIPPED,
                                null,
                                null,
                                reasons(List.of(), Map.of(), why),
                                now));
    }

    /**
     * 실패한 줄은 다음 시각을 늦춘다. 정해진 횟수에 닿았으면 FAILED로 두고 검토 대기(REVIEW)로 보낸다. 1단계에서 이미 걸린 규칙은 근거로 함께 남긴다. 오류
     * 기록에는 예외 종류만 남긴다(메시지에 본문 일부가 들어갈 수 있다).
     */
    private void handleFailure(Row row, RuleResult ruleResult, RuntimeException failure) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean permanent = row.attempt() >= properties.maxAttempts();
        String note = ruleResult == null ? CHECK_FAILED : API_FAILED;
        try {
            tx.executeWithoutResult(
                    status -> {
                        if (permanent
                                && repository.hasNewerRow(
                                        row.targetType(), row.targetId(), row.id())) {
                            // 이전 수정본의 실패는 검토 대기로 올리지 않는다. 새 줄이 따로 판정한다.
                            repository.complete(
                                    row,
                                    ModerationStatus.SKIPPED,
                                    null,
                                    null,
                                    reasons(List.of(), Map.of(), "SUPERSEDED"),
                                    now);
                        } else if (permanent) {
                            ModerationVerdict verdict =
                                    ruleResult == null
                                            ? ModerationVerdict.REVIEW
                                            : ruleResult.verdict().max(ModerationVerdict.REVIEW);
                            repository.complete(
                                    row,
                                    ModerationStatus.FAILED,
                                    ruleResult == null
                                            ? ModerationStage.RULE
                                            : ModerationStage.MODERATION_API,
                                    verdict,
                                    reasons(
                                            ruleResult == null ? List.of() : ruleResult.codes(),
                                            Map.of(),
                                            note),
                                    now);
                        } else {
                            repository.scheduleRetry(row, now.plus(backoff(row.attempt())), now);
                        }
                    });
        } catch (RuntimeException recordFailure) {
            // 기록하지 못해도 임대가 끝나면 다시 가져가므로 줄은 사라지지 않는다.
            log.warn(
                    "content moderation failure not recorded: id={}, error={}",
                    row.id(),
                    recordFailure.getClass().getSimpleName());
        }
        log.warn(
                "content moderation {}: id={}, target={}:{}, attempt={}, error={}",
                permanent ? "failed" : "deferred",
                row.id(),
                row.targetType(),
                row.targetId(),
                row.attempt(),
                failure.getClass().getSimpleName());
    }

    /** 1회 실패 뒤 기본 지연, 이후 두 배씩 늘리고 상한에서 멈춘다. */
    Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        Duration delay = properties.retryBaseDelay().multipliedBy(multiplier);
        return delay.compareTo(properties.retryMaxDelay()) > 0 ? properties.retryMaxDelay() : delay;
    }

    /** 판정 근거 JSON. 본문은 넣지 않는다. 규칙 이름, 점수, 메모만 넣는다. */
    static String reasons(List<String> codes, Map<String, Double> scores, String note) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!codes.isEmpty()) body.put("rules", codes);
        if (!scores.isEmpty()) body.put("scores", scores);
        if (note != null) body.put("note", note);
        if (body.isEmpty()) return null;
        String json = JSON.writeValueAsString(body);
        if (json.length() > MAX_REASONS_LENGTH && body.remove("scores") != null) {
            // 중간을 자르면 JSON이 깨져 관리자 화면이 근거를 못 읽는다. 점수를 통째로 빼고 규칙과 메모를 남긴다.
            json = JSON.writeValueAsString(body);
        }
        return json.length() > MAX_REASONS_LENGTH ? null : json;
    }

    private record ApiCandidate(Row row, RuleResult rules, String input) {}
}
