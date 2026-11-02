package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ikae.snowthing.domain.member.dto.MemberPasswordChangeRequest;
import com.ikae.snowthing.domain.member.dto.MemberProfileUpdateRequest;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.*;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class MemberSettingsServiceTest {
    @Mock MemberRepository members;
    @Mock ResortRepository resorts;
    @Mock RidingStyleRepository styles;
    @Mock MemberResortRepository memberResorts;
    @Mock MemberRidingStyleRepository memberStyles;
    @Mock PasswordEncoder encoder;
    @Spy EmailNormalizer normalizer = new EmailNormalizer();
    @InjectMocks MemberService service;
    Member member;

    @BeforeEach
    void setup() {
        member =
                Member.builder()
                        .publicId("member-uuid")
                        .email("user@example.com")
                        .nickname("snowrider")
                        .password("old-hash")
                        .profileImageUrl("old-image")
                        .bio("bio")
                        .departureRegion("Seoul")
                        .build();
        lenient().when(members.findByEmail("user@example.com")).thenReturn(Optional.of(member));
        lenient()
                .when(members.findByEmailForUpdate("user@example.com"))
                .thenReturn(Optional.of(member));
    }

    @Test
    void wrongCurrentPasswordCannotChangeHash() {
        when(encoder.matches("wrong", "old-hash")).thenReturn(false);
        assertThatThrownBy(
                        () ->
                                service.changePassword(
                                        "user@example.com",
                                        new MemberPasswordChangeRequest("wrong", "12345678")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        assertThat(member.getPassword()).isEqualTo("old-hash");
        verify(encoder, never()).encode(any());
    }

    @Test
    void socialAccountDoesNotAcceptLocalPasswordChange() {
        member.changePassword(null);
        assertThatThrownBy(
                        () ->
                                service.changePassword(
                                        "user@example.com",
                                        new MemberPasswordChangeRequest("anything", "12345678")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_CHANGE_UNAVAILABLE);
        verifyNoInteractions(encoder);
    }

    @Test
    void validPasswordChangeReturnsIdentityForSessionInvalidation() {
        when(encoder.matches("current", "old-hash")).thenReturn(true);
        when(encoder.encode("12345678")).thenReturn("new-hash");
        assertThat(
                        service.changePassword(
                                "user@example.com",
                                new MemberPasswordChangeRequest("current", "12345678")))
                .isEqualTo("member-uuid");
        assertThat(member.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void resettingPhotoKeepsOtherProfileFields() {
        service.setMyProfileImage("user@example.com", null);
        assertThat(member.getProfileImageUrl()).isNull();
        assertThat(member.getNickname()).isEqualTo("snowrider");
        assertThat(member.getBio()).isEqualTo("bio");
        verifyNoInteractions(memberResorts, memberStyles);
    }

    @Test
    void invalidMasterIdCannotEraseExistingPreferencesOrEditProfile() {
        when(resorts.findAllById(any())).thenReturn(List.of());
        when(styles.findAllById(any())).thenReturn(List.of());
        var request =
                MemberProfileUpdateRequest.builder()
                        .resortIds(List.of())
                        .ridingStyleIds(List.of())
                        .nickname("newname")
                        .resortIds(List.of(99L))
                        .build();
        assertThatThrownBy(() -> service.updateMyProfile("user@example.com", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(member.getNickname()).isEqualTo("snowrider");
        verifyNoInteractions(memberResorts, memberStyles);
    }

    @Test
    void currentNicknameIsAvailableWithoutTreatingSelfAsDuplicate() {
        assertThat(service.isNicknameAvailable("user@example.com", "snowrider")).isTrue();
        verify(members, never()).existsByNickname(any());
    }

    @Test
    void concurrentNicknameCollisionUsesTypedDatabaseConstraintInsteadOfErrorMessage() {
        when(resorts.findAllById(any())).thenReturn(List.of());
        when(styles.findAllById(any())).thenReturn(List.of());
        var sql =
                new org.hibernate.exception.ConstraintViolationException(
                        "untrusted database message",
                        new java.sql.SQLException(),
                        "`member`.`nickname`");
        doThrow(new org.springframework.dao.DataIntegrityViolationException("write failed", sql))
                .when(members)
                .flush();
        var request =
                MemberProfileUpdateRequest.builder()
                        .resortIds(List.of())
                        .ridingStyleIds(List.of())
                        .nickname("othername")
                        .build();
        assertThatThrownBy(() -> service.updateMyProfile("user@example.com", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        verifyNoInteractions(memberResorts, memberStyles);
    }
}
