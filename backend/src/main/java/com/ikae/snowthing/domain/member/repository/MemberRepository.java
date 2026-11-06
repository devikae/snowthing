package com.ikae.snowthing.domain.member.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByProfileImageUrl(String profileImageUrl);

    Optional<Member> findByPublicId(String publicId);

    // nickname UNIQUE 인덱스의 앞부분 범위 검색을 쓴다. 닉네임 순이라 정확히 같은 닉네임이 맨 위에 온다.
    @Query(
            "SELECT m FROM Member m WHERE m.nickname LIKE :prefix ESCAPE '!'"
                    + " AND m.status = com.ikae.snowthing.domain.member.entity.MemberStatus.ACTIVE"
                    + " AND m.id <> :viewerId ORDER BY m.nickname ASC, m.id ASC")
    List<Member> searchActiveByNicknamePrefix(
            @Param("prefix") String prefix, @Param("viewerId") Long viewerId, Pageable pageable);

    Optional<Member> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.email = :email")
    Optional<Member> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);
}
