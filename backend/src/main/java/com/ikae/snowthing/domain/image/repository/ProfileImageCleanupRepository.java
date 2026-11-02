package com.ikae.snowthing.domain.image.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.image.entity.ProfileImageCleanup;

public interface ProfileImageCleanupRepository extends JpaRepository<ProfileImageCleanup, String> {
    List<ProfileImageCleanup> findByNotBeforeLessThanEqualOrderByNotBeforeAsc(
            Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ProfileImageCleanup job where job.key=:key")
    Optional<ProfileImageCleanup> findForUpdate(@Param("key") String key);
}
