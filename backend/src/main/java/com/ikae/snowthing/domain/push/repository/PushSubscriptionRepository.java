package com.ikae.snowthing.domain.push.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.push.entity.PushSubscription;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    /**
     * endpoint 유니크 키로 한 문장에 넣거나 고친다. 같은 기기를 두 번 동시에 등록해도 행은 하나다. 다른 계정이 같은 기기에서 켜면 회원이 바뀐다. 공유 기기에서
     * 이전 계정의 알림이 계속 오지 않게 하려는 것이다.
     *
     * <p>문장 하나로 끝나므로 자기 트랜잭션으로 바로 커밋해 락을 오래 잡지 않는다. 같은 키를 동시에 넣으면 InnoDB가 교착으로 한쪽을 되돌릴 수 있어 호출자가 다시
     * 시도한다.
     */
    @Transactional
    @Modifying
    @Query(
            value =
                    "INSERT INTO push_subscription"
                            + " (member_id, endpoint, endpoint_hash, p256dh, auth, created_at,"
                            + " last_used_at)"
                            + " VALUES (:memberId, :endpoint, :endpointHash, :p256dh, :auth, :now,"
                            + " :now)"
                            + " ON DUPLICATE KEY UPDATE member_id = VALUES(member_id),"
                            + " endpoint = VALUES(endpoint), p256dh = VALUES(p256dh),"
                            + " auth = VALUES(auth), last_used_at = VALUES(last_used_at)",
            nativeQuery = true)
    int upsert(
            @Param("memberId") Long memberId,
            @Param("endpoint") String endpoint,
            @Param("endpointHash") String endpointHash,
            @Param("p256dh") String p256dh,
            @Param("auth") String auth,
            @Param("now") LocalDateTime now);

    /** 회원의 구독 중 최근 {@code keep}개만 남기고 지운다. MySQL은 같은 테이블을 바로 서브쿼리로 못 읽어 파생 테이블로 한 번 감싼다. */
    @Transactional
    @Modifying
    @Query(
            value =
                    "DELETE FROM push_subscription WHERE member_id = :memberId"
                            + " AND push_subscription_id NOT IN (SELECT id FROM ("
                            + " SELECT push_subscription_id AS id FROM push_subscription"
                            + " WHERE member_id = :memberId"
                            + " ORDER BY last_used_at DESC, push_subscription_id DESC"
                            + " LIMIT :keep) recent)",
            nativeQuery = true)
    int deleteOlderThanRecent(@Param("memberId") Long memberId, @Param("keep") int keep);

    @Transactional
    @Modifying
    @Query(
            "DELETE FROM PushSubscription s"
                    + " WHERE s.memberId = :memberId AND s.endpointHash = :endpointHash")
    int deleteByMemberAndEndpointHash(
            @Param("memberId") Long memberId, @Param("endpointHash") String endpointHash);

    List<PushSubscription> findAllByMemberIdInOrderByIdAsc(Collection<Long> memberIds);

    @Transactional
    @Modifying
    @Query("DELETE FROM PushSubscription s WHERE s.id IN :ids")
    int deleteByIds(@Param("ids") Collection<Long> ids);

    @Transactional
    @Modifying
    @Query("UPDATE PushSubscription s SET s.lastUsedAt = :now WHERE s.id IN :ids")
    int touch(@Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);
}
