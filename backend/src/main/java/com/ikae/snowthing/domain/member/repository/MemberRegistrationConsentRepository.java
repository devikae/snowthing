package com.ikae.snowthing.domain.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.member.entity.MemberRegistrationConsent;

public interface MemberRegistrationConsentRepository
        extends JpaRepository<MemberRegistrationConsent, Long> {}
