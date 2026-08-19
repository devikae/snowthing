package com.ikae.snowthing.domain.member.repository;

import com.ikae.snowthing.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByPublicId(String publicId);

    Optional<Member> findByEmail(String email);
}
