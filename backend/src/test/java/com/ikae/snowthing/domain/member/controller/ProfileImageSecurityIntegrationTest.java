package com.ikae.snowthing.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.domain.image.service.ImageService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileImageSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @MockitoBean ImageService images;

    @Test
    void uploadBindsImageOnlyToAuthenticatedMemberAndIgnoresForgedTarget() throws Exception {
        Member self =
                members.saveAndFlush(
                        Member.builder()
                                .email("photo-self@example.com")
                                .nickname("사진본인")
                                .password("hash")
                                .build());
        Member other =
                members.saveAndFlush(
                        Member.builder()
                                .email("photo-other@example.com")
                                .nickname("사진타인")
                                .password("hash")
                                .build());
        String url = "https://images.snowthing.org/public/profiles/test.jpg";
        when(images.uploadProfileImage(any()))
                .thenReturn(new ImageUploadResponse(url, "public/profiles/test.jpg"));
        mvc.perform(
                        multipart("/api/v1/images/profile")
                                .file(
                                        new MockMultipartFile(
                                                "file", "photo.jpg", "image/jpeg", new byte[] {1}))
                                .param("email", other.getEmail())
                                .param("memberId", other.getPublicId())
                                .with(user(new CustomUserDetails(self)))
                                .with(csrf()))
                .andExpect(status().isCreated());
        assertThat(members.findByEmail(self.getEmail()).orElseThrow().getProfileImageUrl())
                .isEqualTo(url);
        assertThat(members.findByEmail(other.getEmail()).orElseThrow().getProfileImageUrl())
                .isNull();
    }

    @Test
    void anonymousUploadIsRejected() throws Exception {
        mvc.perform(
                        multipart("/api/v1/images/profile")
                                .file(
                                        new MockMultipartFile(
                                                "file", "photo.jpg", "image/jpeg", new byte[] {1}))
                                .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadWithoutCsrfIsRejected() throws Exception {
        Member self =
                Member.builder()
                        .email("photo-csrf@example.com")
                        .nickname("csrf")
                        .password("hash")
                        .build();
        mvc.perform(
                        multipart("/api/v1/images/profile")
                                .file(
                                        new MockMultipartFile(
                                                "file", "photo.jpg", "image/jpeg", new byte[] {1}))
                                .with(user(new CustomUserDetails(self))))
                .andExpect(status().isForbidden());
    }
}
