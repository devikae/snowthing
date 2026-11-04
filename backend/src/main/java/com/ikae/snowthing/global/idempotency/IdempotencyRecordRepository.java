package com.ikae.snowthing.global.idempotency;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByAuthorKeyAndTargetTypeAndIdempotencyKey(
            String authorKey, IdempotencyTarget targetType, String idempotencyKey);

    /**
     * 처음 응답을 채운다. 작성 서비스가 벌크 쿼리로 영속성 컨텍스트를 비우면(clearAutomatically) 기록 엔티티가 분리돼 변경 감지로는 저장되지 않으므로
     * UPDATE 쿼리로 직접 쓴다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE IdempotencyRecord r SET r.responseBody = :body WHERE r.id = :id")
    int saveResponse(@Param("id") Long id, @Param("body") String body);

    /** 보관 기간이 지난 기록을 한 번에 최대 limit건 지운다. 긴 잠금을 피하려고 나눠 지운다. */
    @Transactional
    @Modifying
    @Query(
            value =
                    "DELETE FROM idempotency_record WHERE created_at < :cutoff"
                            + " ORDER BY created_at, idempotency_record_id LIMIT :limit",
            nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
