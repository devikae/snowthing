package com.ikae.snowthing.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewResponse;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelPriceSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.carpool.external.FuelPriceSnapshot;
import com.ikae.snowthing.domain.carpool.external.KakaoMobilityClient;
import com.ikae.snowthing.domain.carpool.external.OpinetFuelPriceClient;
import com.ikae.snowthing.domain.carpool.external.PlaceCoordinate;
import com.ikae.snowthing.domain.carpool.external.RouteCalculation;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class CarpoolAutoCalculationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);
    private static final Long RESORT_ID = 1L;

    private final ResortRepository resortRepository = mock(ResortRepository.class);
    private final KakaoMobilityClient kakaoMobilityClient = mock(KakaoMobilityClient.class);
    private final OpinetFuelPriceClient opinetFuelPriceClient = mock(OpinetFuelPriceClient.class);
    private CarpoolAutoCalculationService service;

    @BeforeEach
    void setUp() {
        service =
                new CarpoolAutoCalculationService(
                        resortRepository, kakaoMobilityClient, opinetFuelPriceClient);
        Resort resort =
                Resort.builder()
                        .code("PHOENIX")
                        .name("휘닉스파크")
                        .regionName("강원 평창")
                        .displayOrder(1)
                        .active(true)
                        .routeLatitude(BigDecimal.valueOf(37.5))
                        .routeLongitude(BigDecimal.valueOf(128.3))
                        .build();
        when(resortRepository.findById(RESORT_ID)).thenReturn(Optional.of(resort));
        when(kakaoMobilityClient.calculateRoute(any(), any(), any(), anyString()))
                .thenReturn(new RouteCalculation(BigDecimal.valueOf(100), 10000, 7200, NOW));
        when(opinetFuelPriceClient.getAveragePrice(CarpoolFuelType.GASOLINE))
                .thenReturn(
                        new FuelPriceSnapshot(
                                "B027", BigDecimal.valueOf(1858), LocalDate.of(2026, 10, 3), NOW));
    }

    @Test
    void calculatesOneWayWithCurrentFuelSnapshot() {
        CarpoolAutoPreviewResponse result = service.preview(request(CarpoolTripType.ONE_WAY));

        assertThat(result.distanceKm()).isEqualByComparingTo("100");
        assertThat(result.tollFee()).isEqualTo(10000);
        assertThat(result.fuelPrice()).isEqualByComparingTo("1858");
        assertThat(result.estimatedFuelCost()).isEqualTo(15484);
        assertThat(result.estimatedTotalCost()).isEqualTo(25484);
        assertThat(result.estimatedCostPerPerson()).isEqualTo(8495);
        verify(kakaoMobilityClient, never()).searchPlaces(anyString());
        ArgumentCaptor<PlaceCoordinate> destination =
                ArgumentCaptor.forClass(PlaceCoordinate.class);
        verify(kakaoMobilityClient)
                .calculateRoute(any(), any(), destination.capture(), anyString());
        assertThat(destination.getValue().latitude()).isEqualByComparingTo("37.5");
        assertThat(destination.getValue().longitude()).isEqualByComparingTo("128.3");
    }

    @Test
    void calculatesOutboundAndReturnRoutesSeparatelyForRoundTrip() {
        when(kakaoMobilityClient.calculateRoute(any(), any(), any(), anyString()))
                .thenReturn(new RouteCalculation(BigDecimal.valueOf(100), 10000, 7200, NOW))
                .thenReturn(
                        new RouteCalculation(
                                BigDecimal.valueOf(105), 12000, 7500, NOW.plusMinutes(1)));

        CarpoolAutoPreviewResponse result = service.preview(request(CarpoolTripType.ROUND_TRIP));

        assertThat(result.distanceKm()).isEqualByComparingTo("205");
        assertThat(result.tollFee()).isEqualTo(22000);
        assertThat(result.durationSeconds()).isEqualTo(14700);
        assertThat(result.estimatedFuelCost()).isEqualTo(31741);
        assertThat(result.estimatedTotalCost()).isEqualTo(53741);
        assertThat(result.estimatedCostPerPerson()).isEqualTo(17914);
        assertThat(result.routeCalculatedAt()).isEqualTo(NOW.plusMinutes(1));
        verify(kakaoMobilityClient, times(2)).calculateRoute(any(), any(), any(), anyString());
    }

    @Test
    void calculatesWithManualDistanceAndTollWhenKakaoRouteIsUnavailable() {
        CarpoolAutoPreviewResponse result = service.preview(manualRequest(null, 20000));

        assertThat(result.routeSource()).isEqualTo(CarpoolRouteSource.MANUAL);
        assertThat(result.distanceKm()).isEqualByComparingTo("300");
        assertThat(result.tollFee()).isEqualTo(20000);
        assertThat(result.routeCalculatedAt()).isNull();
        verify(kakaoMobilityClient, never()).calculateRoute(any(), any(), any(), anyString());
    }

    @Test
    void usesValidatedUserFuelPriceWhenOpinetAndCacheAreUnavailable() {
        when(opinetFuelPriceClient.getAveragePrice(CarpoolFuelType.GASOLINE))
                .thenThrow(new CustomException(ErrorCode.FUEL_PRICE_PROVIDER_UNAVAILABLE));

        CarpoolAutoPreviewResponse result = service.preview(manualRequest("2000", 0));

        assertThat(result.fuelPrice()).isEqualByComparingTo("2000");
        assertThat(result.fuelPriceSource()).isEqualTo(CarpoolFuelPriceSource.USER_INPUT);
    }

    @Test
    void rejectsManualTollAboveTwentyThousandWon() {
        assertThatThrownBy(() -> service.preview(manualRequest("2000", 20001)))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_CARPOOL_VALUE));
    }

    private CarpoolAutoPreviewRequest manualRequest(String fuelPrice, int tollFee) {
        return new CarpoolAutoPreviewRequest(
                null,
                null,
                RESORT_ID,
                CarpoolTripType.ROUND_TRIP,
                CarpoolFuelType.GASOLINE,
                BigDecimal.valueOf(12),
                2,
                CarpoolRouteSource.MANUAL,
                BigDecimal.valueOf(300),
                tollFee,
                fuelPrice == null ? null : new BigDecimal(fuelPrice));
    }

    private CarpoolAutoPreviewRequest request(CarpoolTripType tripType) {
        return new CarpoolAutoPreviewRequest(
                BigDecimal.valueOf(127.1),
                BigDecimal.valueOf(37.5),
                RESORT_ID,
                tripType,
                CarpoolFuelType.GASOLINE,
                BigDecimal.valueOf(12),
                2);
    }
}
