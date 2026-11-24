package com.ikae.snowthing.domain.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.admin.service.BannerService;
import com.ikae.snowthing.global.exception.GlobalExceptionHandler;
import com.ikae.snowthing.global.notification.ServerErrorAlerter;

/** 배너 주소 형식 검증만 본다. 관리자 권한은 보안 설정의 /api/v1/admin/** 규칙이 맡는다. */
class AdminBannerControllerTest {

    private static final String IMAGE = "https://images.example.com/banner.jpg";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BannerService service = mock(BannerService.class);
    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new AdminBannerController(service))
                    .setControllerAdvice(
                            new GlobalExceptionHandler(
                                    new StaticListableBeanFactory()
                                            .getBeanProvider(ServerErrorAlerter.class)))
                    .build();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "JaVaScRiPt:alert(1)",
                " javascript:alert(1)",
                "data:text/html,x",
                "vbscript:x",
                "//evil.example.com",
                "/\\evil.example.com",
                "evil.example.com",
                "https://a.example.com/a b"
            })
    @DisplayName("웹 주소나 사이트 안 경로가 아닌 이동 주소는 저장하지 않는다")
    void rejectsUnsafeLinkUrl(String linkUrl) throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/banners")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(IMAGE, linkUrl)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        patch("/api/v1/admin/banners/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(IMAGE, linkUrl)))
                .andExpect(status().isBadRequest());

        verify(service, never()).createBanner(any());
        verify(service, never()).updateBanner(any(), any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"javascript:alert(1)", "data:image/svg+xml,x", "//evil.example.com/a.png"})
    @DisplayName("사진 주소도 같은 형식만 받는다")
    void rejectsUnsafeImageUrl(String imageUrl) throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/banners")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(imageUrl, null)))
                .andExpect(status().isBadRequest());

        verify(service, never()).createBanner(any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://snowthing.example.com/posts/1?a=b#c",
                "http://localhost:3000/x",
                "/resorts/konjiam"
            })
    @DisplayName("웹 주소와 사이트 안 경로는 그대로 받는다")
    void acceptsWebAndSitePaths(String linkUrl) throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/banners")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(IMAGE, linkUrl)))
                .andExpect(status().isOk());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("이동 주소는 비워 둘 수 있고, DB 열보다 긴 주소는 거절한다")
    void linkIsOptionalAndLengthIsBounded() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/banners")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(IMAGE, null)))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post("/api/v1/admin/banners")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(IMAGE, "https://a.example.com/" + "a".repeat(500))))
                .andExpect(status().isBadRequest());
    }

    private String body(String imageUrl, String linkUrl) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "배너");
        body.put("imageUrl", imageUrl);
        body.put("linkUrl", linkUrl);
        body.put("displayOrder", 1);
        body.put("isActive", true);
        return objectMapper.writeValueAsString(body);
    }
}
