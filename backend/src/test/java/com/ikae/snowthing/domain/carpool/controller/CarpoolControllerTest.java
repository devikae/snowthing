package com.ikae.snowthing.domain.carpool.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewResponse;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;
import com.ikae.snowthing.domain.carpool.service.CarpoolAutoCalculationService;
import com.ikae.snowthing.domain.carpool.service.CarpoolExternalApiRateLimiter;
import com.ikae.snowthing.domain.carpool.service.CarpoolService;
import com.ikae.snowthing.domain.post.service.PostSearchRateLimiter;
import com.ikae.snowthing.global.idempotency.IdempotentCreateHandler;
import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.web.ClientIpResolver;

class CarpoolControllerTest {

    private final CarpoolAutoCalculationService calculationService =
            mock(CarpoolAutoCalculationService.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final PostSearchRateLimiter postSearchRateLimiter = mock(PostSearchRateLimiter.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
        mockMvc =
                MockMvcBuilders.standaloneSetup(
                                new CarpoolController(
                                        mock(CarpoolService.class),
                                        calculationService,
                                        mock(CarpoolExternalApiRateLimiter.class),
                                        clientIpResolver,
                                        mock(ContentWriteRateLimiter.class),
                                        mock(IdempotentCreateHandler.class),
                                        postSearchRateLimiter))
                        .build();
    }

    @Test
    void keywordSearchUsesSharedSearchLimit() throws Exception {
        mockMvc.perform(get("/api/v1/carpools").param("keyword", "강남")).andExpect(status().isOk());

        verify(postSearchRateLimiter).checkKeywordSearch("강남", "203.0.113.10");
    }

    @Test
    void listWithoutKeywordIsNotLimited() throws Exception {
        mockMvc.perform(get("/api/v1/carpools")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/carpools").param("keyword", "  ")).andExpect(status().isOk());

        verify(postSearchRateLimiter, never()).checkKeywordSearch(anyString(), anyString());
    }

    @Test
    void previewsRouteAndCurrentFuelPrice() throws Exception {
        LocalDateTime calculatedAt = LocalDateTime.of(2026, 10, 3, 12, 0);
        when(calculationService.preview(any()))
                .thenReturn(
                        new CarpoolAutoPreviewResponse(
                                BigDecimal.valueOf(200),
                                20000,
                                14400,
                                BigDecimal.valueOf(1858),
                                LocalDate.of(2026, 10, 3),
                                calculatedAt,
                                30967,
                                50967,
                                16989,
                                3,
                                CarpoolRouteSource.KAKAO,
                                calculatedAt));

        mockMvc.perform(
                        post("/api/v1/carpools/auto-preview")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "originLongitude": 127.1,
                                          "originLatitude": 37.5,
                                          "destinationResortId": 1,
                                          "tripType": "ROUND_TRIP",
                                          "fuelType": "GASOLINE",
                                          "fuelEfficiency": 12,
                                          "passengerCapacity": 2,
                                          "routeSource": "KAKAO"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceKm").value(200))
                .andExpect(jsonPath("$.fuelPrice").value(1858))
                .andExpect(jsonPath("$.estimatedCostPerPerson").value(16989));
    }

    @Test
    void rejectsPreviewWhenPassengerCapacityExceedsLimit() throws Exception {
        mockMvc.perform(
                        post("/api/v1/carpools/auto-preview")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "originLongitude": 127.1,
                                          "originLatitude": 37.5,
                                          "destinationResortId": 1,
                                          "tripType": "ONE_WAY",
                                          "fuelType": "GASOLINE",
                                          "fuelEfficiency": 12,
                                          "passengerCapacity": 21
                                        }
                                        """))
                .andExpect(status().isBadRequest());
    }
}
