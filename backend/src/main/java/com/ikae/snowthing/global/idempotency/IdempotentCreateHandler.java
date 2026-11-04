package com.ikae.snowthing.global.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import tools.jackson.databind.ObjectMapper;

/**
 * 작성 API의 Idempotency-Key 처리기.
 *
 * <p>헤더가 없으면 예전과 똑같이 쓰기 제한 확인 후 작성만 한다. 헤더가 있으면 다음 순서로 처리한다.
 *
 * <ol>
 *   <li>이미 커밋된 같은 키 기록이 있으면 쓰기 제한을 거치지 않고 처음 응답을 그대로 돌려준다(내용이 다르면 409).
 *   <li>쓰기 제한을 확인한다.
 *   <li>한 트랜잭션 안에서 기록을 먼저 insert하고, 글을 저장하고, 응답 JSON을 기록에 채운다.
 *   <li>같은 키가 동시에 들어오면 늦은 쪽 insert가 UNIQUE 잠금에서 기다리다 충돌한다. 그 트랜잭션은 통째로 롤백되고, 먼저 커밋된 기록의 응답을 돌려준다.
 * </ol>
 */
@Component
public class IdempotentCreateHandler {

    public static final String HEADER = "Idempotency-Key";

    private static final Pattern UUID_PATTERN =
            Pattern.compile(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final IdempotencyRecordRepository records;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotentCreateHandler(
            IdempotencyRecordRepository records,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            Clock clock) {
        this.records = records;
        this.transactions = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param rawKey Idempotency-Key 헤더 값. 없으면 null
     * @param fingerprint 같은 요청인지 판별할 값(요청 본문, 경로 변수). JSON으로 바꿔 해시한다
     * @param beforeWrite 쓰기 제한 확인처럼 새로 작성할 때만 거치는 검사
     * @param write 실제 작성. 서비스의 트랜잭션은 이 처리기의 트랜잭션에 합류한다
     * @return 201 응답. 재전송이면 처음 응답 JSON을 그대로 담는다
     */
    public ResponseEntity<Object> create(
            IdempotencyTarget target,
            String rawKey,
            CustomUserDetails userDetails,
            String clientIp,
            Object fingerprint,
            Runnable beforeWrite,
            Supplier<?> write) {
        if (rawKey == null) {
            beforeWrite.run();
            return ResponseEntity.status(HttpStatus.CREATED).body(write.get());
        }
        String key = normalizeKey(rawKey);
        String authorKey = authorKey(userDetails, clientIp);
        String requestHash = sha256(objectMapper.writeValueAsString(fingerprint));

        Optional<IdempotencyRecord> committed = find(authorKey, target, key);
        if (committed.isPresent()) {
            return replay(committed.get(), requestHash);
        }
        beforeWrite.run();
        try {
            Object body =
                    transactions.execute(
                            status -> {
                                IdempotencyRecord record =
                                        records.saveAndFlush(
                                                new IdempotencyRecord(
                                                        authorKey,
                                                        target,
                                                        key,
                                                        requestHash,
                                                        LocalDateTime.now(clock.withZone(KST))));
                                Object result = write.get();
                                records.saveResponse(
                                        record.getId(), objectMapper.writeValueAsString(result));
                                return result;
                            });
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (DataIntegrityViolationException e) {
            // 같은 키를 먼저 커밋한 요청이 있으면 그 결과를 돌려준다. 없으면 글 쪽 제약 위반이라 그대로 던진다.
            return find(authorKey, target, key)
                    .map(existing -> replay(existing, requestHash))
                    .orElseThrow(() -> e);
        }
    }

    static String normalizeKey(String rawKey) {
        String key = rawKey.strip();
        if (!UUID_PATTERN.matcher(key).matches()) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return key.toLowerCase(Locale.ROOT);
    }

    static String authorKey(CustomUserDetails userDetails, String clientIp) {
        if (userDetails != null
                && userDetails.getMember() != null
                && userDetails.getMember().getId() != null) {
            return "member:" + userDetails.getMember().getId();
        }
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        return "ip:" + sha256(ip).substring(0, 32);
    }

    private Optional<IdempotencyRecord> find(
            String authorKey, IdempotencyTarget target, String key) {
        return records.findByAuthorKeyAndTargetTypeAndIdempotencyKey(authorKey, target, key);
    }

    private ResponseEntity<Object> replay(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash) || record.getResponseBody() == null) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        // 처음 응답과 같은 바이트를 그대로 보낸다. 응답 DTO로 되돌리지 않아 DTO 모양과 상관없이 동작한다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(record.getResponseBody().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }
}
