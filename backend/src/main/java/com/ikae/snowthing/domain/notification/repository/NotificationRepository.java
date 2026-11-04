package com.ikae.snowthing.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.notification.entity.Notification;

public interface NotificationRepository
        extends JpaRepository<Notification, Long>, NotificationRepositoryCustom {

    /** 배지 개수. (receiver_id, is_read) 접두 범위만 세므로 복합 인덱스 안에서 끝난다. */
    long countByReceiver_IdAndReadFalse(Long receiverId);

    /** 남의 알림은 없는 것으로 보이게 받는 사람까지 조건에 넣는다. */
    Optional<Notification> findByIdAndReceiver_Id(Long id, Long receiverId);

    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE Notification n SET n.read = true"
                    + " WHERE n.receiver.id = :receiverId AND n.read = false")
    int markAllRead(@Param("receiverId") Long receiverId);

    /**
     * 보관 기간이 지난 알림을 PK 순서로 최대 {@code limit}건 지운다. 오래된 행이 PK 앞쪽에 몰려 있어 앞에서부터 읽다가 멈추므로 created_at 인덱스
     * 없이도 짧게 끝나고, 한 번에 잡는 락도 limit 건 근처로 제한된다.
     */
    @Transactional
    @Modifying
    @Query(
            value =
                    "DELETE FROM notification WHERE created_at < :cutoff"
                            + " ORDER BY notification_id LIMIT :limit",
            nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
