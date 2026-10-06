package com.ikae.snowthing.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.service.AuthService;
import com.ikae.snowthing.domain.email.service.EmailAvailabilityIpRateLimiter;
import com.ikae.snowthing.domain.email.service.EmailVerificationService;
import com.ikae.snowthing.domain.email.service.VerifiedMemberRegistrationService;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.service.MemberService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.web.ClientIpResolver;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private MemberService memberService;

    @Mock private AuthService authService;

    @Mock private VerifiedMemberRegistrationService registrationService;

    @Mock private EmailVerificationService emailVerificationService;

    @Mock private EmailAvailabilityIpRateLimiter emailAvailabilityIpRateLimiter;

    @Mock private ClientIpResolver clientIpResolver;

    @InjectMocks private MemberController memberController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(memberController).build();
    }

    @Test
    @DisplayName("올바른 회원가입 요청 시 201 Created 응답이 반환되어야 한다")
    void signUp_ValidRequest_Returns201() throws Exception {
        // given
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("valid@snowthing.com")
                        .password("Password123!")
                        .emailVerificationToken("verified-token")
                        .nickname("정상닉네임")
                        .build();

        MemberSignUpResponse response =
                MemberSignUpResponse.builder()
                        .publicId("uuid-1234")
                        .email("valid@snowthing.com")
                        .nickname("정상닉네임")
                        .build();

        given(registrationService.register(any(MemberSignUpRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(
                        post("/api/v1/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").value("uuid-1234"))
                .andExpect(jsonPath("$.email").value("valid@snowthing.com"))
                .andExpect(jsonPath("$.nickname").value("정상닉네임"));
    }

    @Test
    @DisplayName("이메일 형식이 잘못된 요청 시 400 Bad Request 에러가 발생해야 한다")
    void signUp_InvalidEmail_Returns400() throws Exception {
        // given
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("invalid-email-format")
                        .password("Password123!")
                        .nickname("정상닉네임")
                        .build();

        // when & then
        mockMvc.perform(
                        post("/api/v1/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입 비밀번호가 8자 미만이면 400 Bad Request를 반환한다")
    void signUp_ShortPassword_Returns400() throws Exception {
        // given
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("valid@snowthing.com")
                        .password("Ab1!")
                        .nickname("정상닉네임")
                        .build();

        // when & then
        mockMvc.perform(
                        post("/api/v1/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입 비밀번호에 영문 대문자가 없으면 400 Bad Request를 반환한다")
    void signUp_PasswordWithoutUppercase_Returns400() throws Exception {
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("valid@snowthing.com")
                        .password("password123!")
                        .nickname("정상닉네임")
                        .build();

        mockMvc.perform(
                        post("/api/v1/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입 비밀번호에 특수문자가 없으면 400 Bad Request를 반환한다")
    void signUp_PasswordWithoutSpecialCharacter_Returns400() throws Exception {
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("valid@snowthing.com")
                        .password("Password123")
                        .nickname("정상닉네임")
                        .build();

        mockMvc.perform(
                        post("/api/v1/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("숫자로만 구성된 123123은 회원가입 비밀번호로 허용하지 않는다")
    void signUp_NumericOnlyPassword_Returns400() throws Exception {
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("valid@snowthing.com")
                        .password("123123")
                        .nickname("정상닉네임")
                        .build();

        mockMvc.perform(
                        post("/api/v1/members")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkEmailAvailability_AppliesIpLimitBeforeMemberLookup() throws Exception {
        given(clientIpResolver.resolve(any())).willReturn("203.0.113.10");
        given(emailVerificationService.isEmailAvailable("valid@snowthing.com")).willReturn(true);

        mockMvc.perform(
                        post("/api/v1/members/email-availability")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"valid@snowthing.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        var ordered = inOrder(emailAvailabilityIpRateLimiter, emailVerificationService);
        ordered.verify(emailAvailabilityIpRateLimiter).check("203.0.113.10");
        ordered.verify(emailVerificationService).isEmailAvailable("valid@snowthing.com");
    }

    @Test
    void checkEmailAvailability_WhenIpLimitExceeded_DoesNotLookUpMember() {
        HttpServletRequest httpRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        given(clientIpResolver.resolve(httpRequest)).willReturn("203.0.113.10");
        doThrow(new CustomException(ErrorCode.EMAIL_AVAILABILITY_LIMIT))
                .when(emailAvailabilityIpRateLimiter)
                .check("203.0.113.10");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                memberController.checkEmailAvailability(
                                        new com.ikae.snowthing.domain.email.dto.EmailRequest(
                                                "valid@snowthing.com"),
                                        httpRequest))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_AVAILABILITY_LIMIT));

        verify(emailVerificationService, never()).isEmailAvailable(any());
    }
}
