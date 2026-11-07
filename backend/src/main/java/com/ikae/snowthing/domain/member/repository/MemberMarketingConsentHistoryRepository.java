package com.ikae.snowthing.domain.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.member.entity.MemberMarketingConsentHistory;

public interface MemberMarketingConsentHistoryRepository
        extends JpaRepository<MemberMarketingConsentHistory, Long> {
    Optional<MemberMarketingConsentHistory> findFirstByMemberIdOrderByIdDesc(Long memberId);
}
