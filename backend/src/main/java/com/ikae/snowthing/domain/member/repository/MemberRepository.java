package com.ikae.snowthing.domain.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByPublicId(String publicId);

    Optional<Member> findByEmail(String email);
}
