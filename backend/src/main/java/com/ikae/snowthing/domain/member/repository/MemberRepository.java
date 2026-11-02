package com.ikae.snowthing.domain.member.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

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

    Optional<Member> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.email = :email")
    Optional<Member> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    long countByCreatedAtAfter(java.time.LocalDateTime createdAt);

    org.springframework.data.domain.Page<Member> findByEmailContainingOrNicknameContaining(
            String email, String nickname, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Member> findByStatus(
            com.ikae.snowthing.domain.member.entity.MemberStatus status,
            org.springframework.data.domain.Pageable pageable);
}
