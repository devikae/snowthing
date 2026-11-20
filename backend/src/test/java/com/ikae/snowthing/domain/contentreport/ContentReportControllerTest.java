package com.ikae.snowthing.domain.contentreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.contentreport.entity.ContentReport;
import com.ikae.snowthing.domain.contentreport.repository.ContentReportRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 비회원 신고 API가 보안 설정에서 열려 있는지, CSRF 검사는 그대로인지 HTTP로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PostService postService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private ContentReportRepository reportRepository;

    private String postPublicId;

    @BeforeEach
    void setUp() {
        Member author =
                memberRepository.saveAndFlush(
                        Member.builder()
                                .email("author@report-controller.test")
                                .password("encoded")
                                .nickname("reportauthor")
                                .role(Role.ROLE_USER)
                                .build());
        categoryRepository
                .findByCode("FREE")
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder().name("자유게시판").code("FREE").build()));
        postPublicId =
                postService
                        .createPost(
                                new PostCreateRequest(
                                        "FREE", "신고 대상 글", "본문", false, null, List.of()),
                                new CustomUserDetails(author),
                                "127.0.0.1")
                        .publicId();
    }

    private String body() {
        return "{\"targetType\":\"POST\",\"targetId\":\""
                + postPublicId
                + "\",\"reason\":\"SPAM\",\"detail\":\"광고입니다\"}";
    }

    @Test
    @DisplayName("비로그인도 신고할 수 있고 신고자 IP가 남는다")
    void guestReport_Accepted() throws Exception {
        mockMvc.perform(
                        post("/api/v1/reports")
                                .with(anonymous())
                                .with(csrf())
                                .with(
                                        request -> {
                                            request.setRemoteAddr("203.0.113.30");
                                            return request;
                                        })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body()))
                .andExpect(status().isNoContent());

        List<ContentReport> reports = reportRepository.findAll();
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getReporter()).isNull();
        assertThat(reports.get(0).getReporterIp()).isEqualTo("203.0.113.30");
    }

    @Test
    @DisplayName("비로그인 신고도 CSRF 토큰이 없으면 403이다")
    void guestReport_WithoutCsrf_Forbidden() throws Exception {
        mockMvc.perform(
                        post("/api/v1/reports")
                                .with(anonymous())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body()))
                .andExpect(status().isForbidden());

        assertThat(reportRepository.findAll()).isEmpty();
    }
}
