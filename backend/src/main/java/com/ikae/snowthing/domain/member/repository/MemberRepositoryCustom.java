package com.ikae.snowthing.domain.member.repository;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;

import java.util.Optional;

public interface MemberRepositoryCustom {

    Optional<MemberLoginResponse> findProfileByEmail(String email);
}
