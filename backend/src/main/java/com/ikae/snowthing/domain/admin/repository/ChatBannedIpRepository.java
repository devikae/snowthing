package com.ikae.snowthing.domain.admin.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.admin.entity.ChatBannedIp;

public interface ChatBannedIpRepository extends JpaRepository<ChatBannedIp, Long> {

    List<ChatBannedIp> findAllByOrderByCreatedAtDesc();

    Optional<ChatBannedIp> findByBannedIp(String bannedIp);

    @Query(
            "SELECT c FROM ChatBannedIp c WHERE c.bannedUntil IS NULL OR c.bannedUntil >"
                    + " :currentTime")
    List<ChatBannedIp> findAllActiveBans(@Param("currentTime") LocalDateTime currentTime);
}
