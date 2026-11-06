package com.ikae.snowthing.domain.charactertest.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.charactertest.entity.CharacterGroup;

public interface CharacterGroupRepository extends JpaRepository<CharacterGroup, Long> {

    Optional<CharacterGroup> findByPublicId(String publicId);

    /** 인원 제한과 닉네임 중복 검사를 참여끼리 차례로 하도록 모임 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from CharacterGroup g where g.publicId = :publicId")
    Optional<CharacterGroup> findLockedByPublicId(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from CharacterGroup g where g.id = :id")
    Optional<CharacterGroup> findLockedById(@Param("id") Long id);

    /** 새 참여 시각을 남긴다. 나가기로는 부르지 않아 보관 기간이 늘지 않는다. */
    @Modifying
    @Query("update CharacterGroup g set g.updatedAt = :now where g.id = :id")
    int touchJoinedAt(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 감사 필드(updated_at)를 건드리지 않게 쿼리로 비운다. 만든 사람이 나가도 보관 기간이 늘지 않는다. */
    @Modifying
    @Query("update CharacterGroup g set g.ownerParticipantId = null where g.id = :id")
    int clearOwner(@Param("id") Long id);

    @Query(
            "select g.id from CharacterGroup g where g.updatedAt < :cutoff"
                    + " order by g.updatedAt, g.id")
    List<Long> findExpiredIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    /** 고른 사이에 새 참여가 있었으면 빠지도록 잠그면서 다시 확인한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from CharacterGroup g where g.id in :ids and g.updatedAt < :cutoff")
    List<CharacterGroup> lockExpired(
            @Param("ids") List<Long> ids, @Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("delete from CharacterGroup g where g.id in :ids")
    int deleteAllByIds(@Param("ids") List<Long> ids);
}
