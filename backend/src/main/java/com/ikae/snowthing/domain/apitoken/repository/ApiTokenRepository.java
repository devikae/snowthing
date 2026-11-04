package com.ikae.snowthing.domain.apitoken.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.apitoken.entity.ApiToken;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    @Query("SELECT t FROM ApiToken t JOIN FETCH t.member WHERE t.tokenHash = :tokenHash")
    Optional<ApiToken> findWithMemberByTokenHash(@Param("tokenHash") String tokenHash);

    List<ApiToken> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

    Optional<ApiToken> findByPublicIdAndMemberId(String publicId, Long memberId);

    long countByMemberIdAndRevokedAtIsNullAndExpiresAtAfter(Long memberId, LocalDateTime now);

    /** 마지막 사용 시각은 요청마다 쓰지 않고 간격이 지났을 때만 쓴다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE ApiToken t SET t.lastUsedAt = :now WHERE t.id = :id"
                    + " AND (t.lastUsedAt IS NULL OR t.lastUsedAt < :threshold)")
    int touchLastUsed(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("threshold") LocalDateTime threshold);
}
