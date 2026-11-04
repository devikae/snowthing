package com.ikae.snowthing.domain.answerdraft.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftSaveRequest;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftSaveRequest.EvidenceRef;
import com.ikae.snowthing.domain.search.service.EvidenceId;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/** 형식 검증을 통과한 저장 요청. DB를 보기 전에 만들 수 있는 검사는 모두 여기서 한다. */
record AnswerDraftInput(
        String targetPostPublicId,
        String body,
        List<EvidenceRef> evidence,
        String idempotencyKey,
        String requestHash) {

    static final int MAX_BODY_LENGTH = 1000;
    static final int MAX_EVIDENCE_COUNT = 20;

    private static final Pattern UUID_FORMAT =
            Pattern.compile(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IDEMPOTENCY_KEY_FORMAT =
            Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    static AnswerDraftInput from(AnswerDraftSaveRequest request) {
        if (request == null) {
            throw invalid();
        }
        String target = request.targetPostPublicId();
        if (target == null || !UUID_FORMAT.matcher(target).matches()) {
            throw invalid();
        }
        String body = request.body();
        if (body == null || body.isBlank() || body.length() > MAX_BODY_LENGTH) {
            throw invalid();
        }
        String key = request.idempotencyKey();
        if (key == null || !IDEMPOTENCY_KEY_FORMAT.matcher(key).matches()) {
            throw invalid();
        }
        List<EvidenceRef> raw = request.evidence();
        if (raw == null || raw.isEmpty() || raw.size() > MAX_EVIDENCE_COUNT) {
            throw invalid();
        }
        List<EvidenceRef> evidence = new ArrayList<>(raw.size());
        Set<String> seen = new HashSet<>();
        for (EvidenceRef ref : raw) {
            if (ref == null || ref.chunkHash() == null) {
                throw invalid();
            }
            String evidenceId;
            try {
                evidenceId = EvidenceId.parse(ref.evidenceId()).value();
            } catch (CustomException e) {
                throw invalid();
            }
            String hash = ref.chunkHash().strip();
            if (!SHA256_HEX.matcher(hash).matches() || !seen.add(evidenceId)) {
                throw invalid();
            }
            evidence.add(new EvidenceRef(evidenceId, hash.toLowerCase(Locale.ROOT)));
        }
        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        return new AnswerDraftInput(
                normalizedTarget,
                body,
                List.copyOf(evidence),
                key,
                requestHash(normalizedTarget, body, evidence));
    }

    /** 대상, 본문, 근거(순서 유지)를 구분자로 이어 해시한다. 같은 키로 다른 내용을 보냈는지 판단하는 데만 쓴다. */
    private static String requestHash(String target, String body, List<EvidenceRef> evidence) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(target).append('\u0000').append(body).append('\u0000');
        for (EvidenceRef ref : evidence) {
            canonical.append(ref.evidenceId()).append('=').append(ref.chunkHash()).append(';');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(
                            digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static CustomException invalid() {
        return new CustomException(ErrorCode.ANSWER_DRAFT_INVALID_REQUEST);
    }
}
