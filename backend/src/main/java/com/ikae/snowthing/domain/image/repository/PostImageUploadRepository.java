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

    /** 동시에 지워지는 두 글이 같은 키를 올려도 기본 키 충돌로 글 삭제가 실패하지 않게 한다. 기한은 expireIfPending이 정한다. */
    @Modifying
    @Query(
            value =
                    "INSERT IGNORE INTO post_image_upload(image_key, owner_email, state, due_at)"
                            + " VALUES (:key, 'legacy', 'PENDING', UTC_TIMESTAMP(6))",
            nativeQuery = true)
    int insertIfAbsent(@Param("key") String key);

    /** 이미 지우는 중이거나 지워진 키는 그 상태를 유지한다. */
    @Modifying
    @Query("UPDATE PostImageUpload p SET p.due = :now WHERE p.key = :key AND p.state = 'PENDING'")
    int expireIfPending(@Param("key") String key, @Param("now") Instant now);

    @Modifying
    @Query(
            "DELETE FROM PostImageUpload p WHERE p.state = 'DELETED' AND p.key LIKE 'public/posts/originals/m-%' AND p.due <= :now")
    int deleteCompletedPurges(@Param("now") Instant now);
}
