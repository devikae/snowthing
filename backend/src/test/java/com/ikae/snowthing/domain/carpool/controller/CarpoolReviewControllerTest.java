package com.ikae.snowthing.domain.carpool.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ikae.snowthing.domain.carpool.dto.CarpoolReviewSummaryResponse;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewTag;
import com.ikae.snowthing.domain.carpool.service.CarpoolReviewService;

class CarpoolReviewControllerTest {

    private final CarpoolReviewService service = mock(CarpoolReviewService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new CarpoolReviewController(service))
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
    }

    @Test
    void writesReview() throws Exception {
        mockMvc.perform(
                        post("/api/v1/carpools/p1/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"revieweePublicId":"m1","rating":"GOOD","tags":["ON_TIME"],"comment":"고마워요"}
                                        """))
                .andExpect(status().isCreated());
        verify(service).writeReview(eq("p1"), any(), any());
    }

    @Test
    void rejectsTooLongCommentAndMissingRating() throws Exception {
        String longComment = "가".repeat(101);
        mockMvc.perform(
                        post("/api/v1/carpools/p1/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"revieweePublicId\":\"m1\",\"rating\":\"GOOD\",\"comment\":\""
                                                + longComment
                                                + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post("/api/v1/carpools/p1/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"revieweePublicId\":\"m1\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void routesPublicSummary() throws Exception {
        when(service.summary("m1"))
                .thenReturn(
                        new CarpoolReviewSummaryResponse(
                                3,
                                2,
                                List.of(
                                        new CarpoolReviewSummaryResponse.TagCount(
                                                CarpoolReviewTag.KIND, 2))));

        mockMvc.perform(get("/api/v1/carpools/members/m1/review-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideCount").value(3))
                .andExpect(jsonPath("$.goodCount").value(2))
                .andExpect(jsonPath("$.topTags[0].tag").value("KIND"))
                .andExpect(jsonPath("$.badCount").doesNotExist());
    }

    @Test
    void routesRideDecisions() throws Exception {
        mockMvc.perform(post("/api/v1/carpools/p1/riders/m1/confirm"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/carpools/p1/riders/m1/reject"))
                .andExpect(status().isNoContent());
        verify(service).decideRide(eq("p1"), eq("m1"), eq(true), any());
        verify(service).decideRide(eq("p1"), eq("m1"), eq(false), any());
    }
}
