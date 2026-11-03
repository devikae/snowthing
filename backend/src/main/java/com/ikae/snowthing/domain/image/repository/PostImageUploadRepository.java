package com.ikae.snowthing.domain.image.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.image.entity.PostImageUpload;

public interface PostImageUploadRepository extends JpaRepository<PostImageUpload, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PostImageUpload p WHERE p.key = :key")
    Optional<PostImageUpload> findByKeyForUpdate(@Param("key") String key);

    @Query(
            "SELECT p.key FROM PostImageUpload p WHERE p.state <> 'DELETED' AND p.due <= :now ORDER BY p.due ASC LIMIT 50")
    List<String> findKeysForCleanup(@Param("now") Instant now);

    @Modifying
    @Query(
            "DELETE FROM PostImageUpload p WHERE p.state = 'DELETED' AND p.key LIKE 'public/posts/originals/m-%' AND p.due <= :now")
    int deleteCompletedPurges(@Param("now") Instant now);
}
