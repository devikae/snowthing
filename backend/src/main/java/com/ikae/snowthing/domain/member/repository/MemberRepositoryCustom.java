package com.ikae.snowthing.domain.member.repository;

import java.util.Optional;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;

public interface MemberRepositoryCustom {

    Optional<MemberLoginResponse> findProfileByEmail(String email);
}
