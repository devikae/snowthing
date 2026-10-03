package com.ikae.snowthing.domain.carpool.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ikae.snowthing.domain.carpool.service.CarpoolService;
import com.ikae.snowthing.global.web.ClientIpResolver;

class CarpoolControllerTest {

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(
                            new CarpoolController(
                                    mock(CarpoolService.class), mock(ClientIpResolver.class)))
                    .build();

    @Test
    void previewsCost() throws Exception {
        mockMvc.perform(
                        post("/api/v1/carpools/cost-preview")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "distanceKm": 200,
                                          "fuelEfficiency": 10,
                                          "fuelPrice": 1700,
                                          "tollFee": 10000,
                                          "passengerCapacity": 3
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedFuelCost").value(34000))
                .andExpect(jsonPath("$.estimatedTotalCost").value(44000))
                .andExpect(jsonPath("$.estimatedCostPerPerson").value(11000));
    }
}
