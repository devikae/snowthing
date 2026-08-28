package com.ikae.snowthing.domain.member.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.dto.MemberProfileUpdateRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.entity.*;
import com.ikae.snowthing.domain.member.repository.*;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final ResortRepository resortRepository;
    private final RidingStyleRepository ridingStyleRepository;
    private final MemberResortRepository memberResortRepository;
    private final MemberRidingStyleRepository memberRidingStyleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberSignUpResponse signUp(MemberSignUpRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new CustomAuthException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new CustomAuthException(ErrorCode.DUPLICATE_NICKNAME);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Member member = request.toEntity(encodedPassword);

        Member savedMember = memberRepository.save(member);

        if (!request.getResortIds().isEmpty()) {
            List<Resort> resorts = resortRepository.findAllById(request.getResortIds());
            List<MemberResort> memberResorts =
                    resorts.stream()
                            .map(
                                    resort ->
                                            MemberResort.builder()
                                                    .member(savedMember)
                                                    .resort(resort)
                                                    .build())
                            .toList();
            memberResortRepository.saveAll(memberResorts);
        }

        if (!request.getRidingStyleIds().isEmpty()) {
            List<RidingStyle> ridingStyles =
                    ridingStyleRepository.findAllById(request.getRidingStyleIds());
            List<MemberRidingStyle> memberRidingStyles =
                    ridingStyles.stream()
                            .map(
                                    style ->
                                            MemberRidingStyle.builder()
                                                    .member(savedMember)
                                                    .ridingStyle(style)
                                                    .build())
                            .toList();
            memberRidingStyleRepository.saveAll(memberRidingStyles);
        }

        return MemberSignUpResponse.from(savedMember);
    }

    @Transactional
    public MemberSignUpResponse updateMyProfile(String email, MemberProfileUpdateRequest request) {
        Member member =
                memberRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));

        // 닉네임 변경 시 기존 닉네임과 다르고 타 유저와 중복되는지 사전 검사
        if (!member.getNickname().equals(request.getNickname())
                && memberRepository.existsByNickname(request.getNickname())) {
            throw new CustomAuthException(ErrorCode.DUPLICATE_NICKNAME);
        }

        // 1. 기본 프로필 정보 업데이트 (Dirty Checking)
        member.updateProfile(
                request.getNickname(),
                request.getBio(),
                request.getDepartureRegion(),
                member.getProfileImageUrl());

        // 2. 기존 N:M 선호 스키장 삭제 후 재등록
        memberResortRepository.deleteAllByMemberId(member.getId());
        if (!request.getResortIds().isEmpty()) {
            List<Resort> resorts = resortRepository.findAllById(request.getResortIds());
            List<MemberResort> newMemberResorts =
                    resorts.stream()
                            .map(
                                    resort ->
                                            MemberResort.builder()
                                                    .member(member)
                                                    .resort(resort)
                                                    .build())
                            .toList();
            memberResortRepository.saveAll(newMemberResorts);
        }

        // 3. 기존 N:M 라이딩 성향 삭제 후 재등록
        memberRidingStyleRepository.deleteAllByMemberId(member.getId());
        if (!request.getRidingStyleIds().isEmpty()) {
            List<RidingStyle> ridingStyles =
                    ridingStyleRepository.findAllById(request.getRidingStyleIds());
            List<MemberRidingStyle> newMemberRidingStyles =
                    ridingStyles.stream()
                            .map(
                                    style ->
                                            MemberRidingStyle.builder()
                                                    .member(member)
                                                    .ridingStyle(style)
                                                    .build())
                            .toList();
            memberRidingStyleRepository.saveAll(newMemberRidingStyles);
        }

        return MemberSignUpResponse.from(member);
    }
}
