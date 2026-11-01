package com.ikae.snowthing.domain.member.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.dto.MemberPasswordChangeRequest;
import com.ikae.snowthing.domain.member.dto.MemberProfileUpdateRequest;
import com.ikae.snowthing.domain.member.dto.MemberSettingsResponse;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.entity.*;
import com.ikae.snowthing.domain.member.repository.*;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.exception.CustomException;

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
    private final EmailNormalizer emailNormalizer;

    private Member requireMember(String email) {
        return memberRepository
                .findByEmail(emailNormalizer.normalize(email))
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Member requireMemberForUpdate(String email) {
        return memberRepository
                .findByEmailForUpdate(emailNormalizer.normalize(email))
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public MemberSettingsResponse getMySettings(String email) {
        Member member = requireMember(email);
        return new MemberSettingsResponse(
                member.getPublicId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getBio(),
                member.getDepartureRegion(),
                member.getPassword() != null,
                memberResortRepository.findAllByMemberIdWithResort(member.getId()).stream()
                        .map(
                                item ->
                                        new MemberSettingsResponse.Preference(
                                                item.getResort().getId(),
                                                item.getResort().getName()))
                        .toList(),
                memberRidingStyleRepository
                        .findAllByMemberIdWithRidingStyle(member.getId())
                        .stream()
                        .map(
                                item ->
                                        new MemberSettingsResponse.Preference(
                                                item.getRidingStyle().getId(),
                                                item.getRidingStyle().getStyleName()))
                        .toList());
    }

    public boolean isNicknameAvailable(String email, String nickname) {
        Member member = requireMember(email);
        return member.getNickname().equals(nickname)
                || !memberRepository.existsByNickname(nickname);
    }

    @Transactional
    public String changePassword(String email, MemberPasswordChangeRequest request) {
        Member member = requireMemberForUpdate(email);
        if (member.getPassword() == null) {
            throw new CustomException(ErrorCode.PASSWORD_CHANGE_UNAVAILABLE);
        }
        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new CustomException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        member.changePassword(passwordEncoder.encode(request.newPassword()));
        return member.getPublicId();
    }

    @Transactional
    public void setMyProfileImage(String email, String imageUrl) {
        Member member = requireMemberForUpdate(email);
        member.updateProfile(
                member.getNickname(), member.getBio(), member.getDepartureRegion(), imageUrl);
    }

    @Transactional
    public MemberSignUpResponse signUp(MemberSignUpRequest request) {
        String normalizedEmail = emailNormalizer.normalize(request.getEmail());
        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new CustomAuthException(ErrorCode.DUPLICATE_NICKNAME);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Member member = request.toEntity(encodedPassword, normalizedEmail);

        Member savedMember;
        try {
            savedMember = memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomAuthException(ErrorCode.DUPLICATE_NICKNAME);
        }

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
        Member member = requireMemberForUpdate(email);

        // 닉네임 변경 시 기존 닉네임과 다르고 타 유저와 중복되는지 사전 검사
        if (!member.getNickname().equals(request.getNickname())
                && memberRepository.existsByNickname(request.getNickname())) {
            throw new CustomAuthException(ErrorCode.DUPLICATE_NICKNAME);
        }

        Set<Long> resortIds = new LinkedHashSet<>(request.getResortIds());
        Set<Long> styleIds = new LinkedHashSet<>(request.getRidingStyleIds());
        List<Resort> resorts = resortRepository.findAllById(resortIds);
        List<RidingStyle> ridingStyles = ridingStyleRepository.findAllById(styleIds);
        if (resorts.size() != resortIds.size() || ridingStyles.size() != styleIds.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        // 1. 기본 프로필 정보 업데이트 (Dirty Checking)
        member.updateProfile(
                request.getNickname(),
                request.getBio(),
                request.getDepartureRegion(),
                member.getProfileImageUrl());

        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof ConstraintViolationException violation
                        && MemberUniqueConstraint.fromDatabaseName(violation.getConstraintName())
                                .filter(value -> value == MemberUniqueConstraint.NICKNAME)
                                .isPresent()) {
                    throw new CustomException(ErrorCode.DUPLICATE_NICKNAME, exception);
                }
                cause = cause.getCause();
            }
            throw exception;
        }

        // 2. 기존 N:M 선호 스키장 삭제 후 재등록
        memberResortRepository.deleteAllByMemberId(member.getId());
        if (!request.getResortIds().isEmpty()) {
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
