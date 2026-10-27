package com.ikae.snowthing.domain.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.member.entity.MemberSocialAccount;
import com.ikae.snowthing.domain.member.entity.SocialProvider;

public interface MemberSocialAccountRepository extends JpaRepository<MemberSocialAccount, Long> {

    @EntityGraph(attributePaths = "member")
    Optional<MemberSocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider, String providerUserId);

    List<MemberSocialAccount> findAllByMemberId(Long memberId);
}
