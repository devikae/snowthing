package com.ikae.snowthing.domain.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.service.MemberService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberController memberController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(memberController).build();
    }

    @Test
    @DisplayName("올바른 회원가입 요청 시 201 Created 응답이 반환되어야 한다")
    void signUp_ValidRequest_Returns201() throws Exception {
        // given
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("valid@snowthing.com")
                .password("Password123!")
                .nickname("정상닉네임")
                .build();

        MemberSignUpResponse response = MemberSignUpResponse.builder()
                .publicId("uuid-1234")
                .email("valid@snowthing.com")
                .nickname("정상닉네임")
                .build();

        given(memberService.signUp(any(MemberSignUpRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/members")
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
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("invalid-email-format")
                .password("Password123!")
                .nickname("정상닉네임")
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호 길이가 짧은 요청 시 400 Bad Request 에러가 발생해야 한다")
    void signUp_ShortPassword_Returns400() throws Exception {
        // given
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("valid@snowthing.com")
                .password("short") // 8자 미만
                .nickname("정상닉네임")
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
