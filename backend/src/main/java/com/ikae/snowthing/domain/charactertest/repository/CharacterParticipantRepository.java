package com.ikae.snowthing.domain.charactertest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.charactertest.entity.CharacterParticipant;

public interface CharacterParticipantRepository extends JpaRepository<CharacterParticipant, Long> {

    @Query(
            "select p from CharacterParticipant p join fetch p.group"
                    + " where p.accessTokenHash = :hash")
    Optional<CharacterParticipant> findWithGroupByTokenHash(@Param("hash") String hash);

    /** 모임 인원은 최대 50명이라 한 번에 읽어 서버에서 궁합을 계산한다. ID 순서가 정렬의 보조 기준이다. */
    List<CharacterParticipant> findAllByGroup_IdOrderByIdAsc(Long groupId);

    long countByGroup_Id(Long groupId);

    boolean existsByGroup_IdAndNickname(Long groupId, String nickname);

    @Modifying
    @Query("delete from CharacterParticipant p where p.id = :id")
    int deleteOneById(@Param("id") Long id);

    @Modifying
    @Query("delete from CharacterParticipant p where p.group.id in :groupIds")
    int deleteAllByGroupIds(@Param("groupIds") List<Long> groupIds);
}
