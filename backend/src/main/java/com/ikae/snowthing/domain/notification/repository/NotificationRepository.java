package com.ikae.snowthing.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByPublicIdAndRecipientId(String publicId, Long recipientId);

    List<Notification> findByRecipientIdAndIdLessThanOrderByIdDesc(
            Long recipientId, Long cursorId, Pageable pageable);

    List<Notification> findByRecipientIdOrderByIdDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @Modifying
    @Query(
            "UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP WHERE n.recipient.id = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") Long recipientId);

    @Modifying
    @Query(
            "DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.createdAt < :readCutoff")
    int deleteReadBefore(@Param("readCutoff") LocalDateTime readCutoff);

    @Modifying
    @Query(
            "DELETE FROM Notification n WHERE n.readAt IS NULL AND n.createdAt < :unreadCutoff")
    int deleteUnreadBefore(@Param("unreadCutoff") LocalDateTime unreadCutoff);
}
