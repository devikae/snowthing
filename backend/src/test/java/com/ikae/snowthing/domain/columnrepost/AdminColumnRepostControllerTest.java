package com.ikae.snowthing.domain.columnrepost;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminColumnRepostControllerTest {

    private static final String URL = "/api/v1/admin/column-repost/publish-next";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ColumnRepostService service;

    @Test
    @DisplayName("관리자는 다음 칼럼을 바로 올리고 남은 개수를 받는다")
    void adminPublishesNext() throws Exception {
        given(service.publishNext()).willReturn(ColumnRepostService.Result.PUBLISHED);
        given(service.pendingCount()).willReturn(43L);

        mockMvc.perform(post(URL).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("PUBLISHED"))
                .andExpect(jsonPath("$.pending").value(43));
    }

    @Test
    @DisplayName("일반 회원과 비로그인은 부를 수 없다")
    void othersAreRejected() throws Exception {
        mockMvc.perform(post(URL).with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL).with(csrf())).andExpect(status().is4xxClientError());

        verify(service, never()).publishNext();
    }
}
