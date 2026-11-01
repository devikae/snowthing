package com.ikae.snowthing.domain.member.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ikae.snowthing.domain.member.dto.MemberPasswordChangeRequest;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.service.MemberService;
import com.ikae.snowthing.domain.member.service.ProfileActionRateLimiter;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.MemberSessionRegistry;

@ExtendWith(MockitoExtension.class)
class MemberSettingsControllerTest {
    @Mock MemberService memberService;
    @Mock ProfileActionRateLimiter limiter;
    @Mock MemberSessionRegistry sessions;
    @InjectMocks MemberController controller;
    CustomUserDetails user;

    @BeforeEach
    void setup() {
        user =
                new CustomUserDetails(
                        Member.builder()
                                .email("self@example.com")
                                .publicId("self-id")
                                .nickname("self")
                                .build());
    }

    @Test
    void sessionsAreInvalidatedOnlyAfterPasswordChangeReturns() {
        var request = new MemberPasswordChangeRequest("old", "12345678");
        when(memberService.changePassword("self@example.com", request)).thenReturn("self-id");
        assertThat(controller.changePassword(user, request).getStatusCode().value()).isEqualTo(204);
        var order = inOrder(limiter, memberService, sessions);
        order.verify(limiter).check("self@example.com", ProfileActionRateLimiter.Action.PASSWORD);
        order.verify(memberService).changePassword("self@example.com", request);
        order.verify(sessions).invalidateAll("self-id");
    }

    @Test
    void failedPasswordChangeCannotInvalidateSessions() {
        var request = new MemberPasswordChangeRequest("wrong", "12345678");
        when(memberService.changePassword("self@example.com", request))
                .thenThrow(new CustomException(ErrorCode.CURRENT_PASSWORD_MISMATCH));
        assertThatThrownBy(() -> controller.changePassword(user, request))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(sessions);
    }

    @Test
    void throttledRequestsDoNotReachPasswordService() {
        doThrow(new CustomException(ErrorCode.PROFILE_ACTION_LIMIT))
                .when(limiter)
                .check(any(), any());
        assertThatThrownBy(
                        () ->
                                controller.changePassword(
                                        user, new MemberPasswordChangeRequest("old", "12345678")))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(memberService, sessions);
    }
}
