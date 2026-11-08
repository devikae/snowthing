package com.ikae.snowthing.domain.seasonlog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.seasonlog.entity.SeasonShare;

public interface SeasonShareRepository extends JpaRepository<SeasonShare, Long> {

    Optional<SeasonShare> findByMember_IdAndSeasonStartYear(Long memberId, int seasonStartYear);

    @Query("SELECT s FROM SeasonShare s JOIN FETCH s.member WHERE s.shareToken = :shareToken")
    Optional<SeasonShare> findByShareTokenWithMember(@Param("shareToken") String shareToken);
}
