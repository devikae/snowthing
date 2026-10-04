package com.ikae.snowthing.domain.carpool.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.carpool.CarpoolLimits;
import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolPlaceResponse;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CarpoolAutoCalculationService {

    private final ResortRepository resortRepository;
    private final KakaoMobilityClient kakaoMobilityClient;
    private final OpinetFuelPriceClient opinetFuelPriceClient;

    public List<CarpoolPlaceResponse> searchPlaces(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < CarpoolLimits.MIN_PLACE_QUERY_LENGTH
                || normalizedQuery.length() > CarpoolLimits.MAX_PLACE_QUERY_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
        return kakaoMobilityClient.searchPlaces(normalizedQuery).stream()
                .map(CarpoolPlaceResponse::from)
                .toList();
    }

    public CarpoolAutoPreviewResponse preview(CarpoolAutoPreviewRequest request) {
        Resort resort = findResort(request.destinationResortId());
        PlaceCoordinate destination = findDestination(resort);
        ResolvedRoute route = resolveRoute(request, destination);
        FuelPriceSnapshot fuelPrice = resolveFuelPrice(request);
        CarpoolCostCalculator.Calculation calculation =
                CarpoolCostCalculator.calculate(
                        route.distanceKm(),
                        request.fuelEfficiency(),
                        fuelPrice.pricePerLiter(),
                        route.tollFee(),
                        request.passengerCapacity());

        return new CarpoolAutoPreviewResponse(
                route.distanceKm(),
                route.tollFee(),
                route.durationSeconds(),
                fuelPrice.pricePerLiter(),
                fuelPrice.tradeDate(),
                fuelPrice.observedAt(),
                fuelPrice.source(),
                calculation.estimatedFuelCost(),
                calculation.estimatedTotalCost(),
                calculation.estimatedCostPerPerson(),
                calculation.totalPassengerCount(),
                route.source(),
                route.calculatedAt());
    }

    private ResolvedRoute resolveRoute(
            CarpoolAutoPreviewRequest request, PlaceCoordinate destination) {
        if (request.routeSource() == CarpoolRouteSource.MANUAL) {
            validateManualRoute(request.manualDistanceKm(), request.manualTollFee());
            return new ResolvedRoute(
                    request.manualDistanceKm(),
                    request.manualTollFee(),
                    0,
                    CarpoolRouteSource.MANUAL,
                    null);
        }
        if (request.routeSource() != CarpoolRouteSource.KAKAO
                || request.originLongitude() == null
                || request.originLatitude() == null) {
            throw new CustomException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
        RouteCalculation outboundRoute =
                kakaoMobilityClient.calculateRoute(
                        request.originLongitude(),
                        request.originLatitude(),
                        destination,
                        toKakaoFuel(request.fuelType()));
        RouteCalculation returnRoute =
                request.tripType() == CarpoolTripType.ROUND_TRIP
                        ? kakaoMobilityClient.calculateRoute(
                                destination.longitude(),
                                destination.latitude(),
                                new PlaceCoordinate(
                                        "출발지",
                                        "",
                                        "",
                                        request.originLongitude(),
                                        request.originLatitude()),
                                toKakaoFuel(request.fuelType()))
                        : null;
        BigDecimal totalDistance = outboundRoute.distanceKm();
        int totalToll = outboundRoute.tollFee();
        int totalDuration = outboundRoute.durationSeconds();
        LocalDateTime calculatedAt = outboundRoute.calculatedAt();
        if (returnRoute != null) {
            totalDistance = totalDistance.add(returnRoute.distanceKm());
            totalToll = Math.addExact(totalToll, returnRoute.tollFee());
            totalDuration = Math.addExact(totalDuration, returnRoute.durationSeconds());
            if (returnRoute.calculatedAt().isAfter(calculatedAt)) {
                calculatedAt = returnRoute.calculatedAt();
            }
        }
        return new ResolvedRoute(
                totalDistance, totalToll, totalDuration, CarpoolRouteSource.KAKAO, calculatedAt);
    }

    private FuelPriceSnapshot resolveFuelPrice(CarpoolAutoPreviewRequest request) {
        try {
            return opinetFuelPriceClient.getAveragePrice(request.fuelType());
        } catch (CustomException exception) {
            if (exception.getErrorCode() != ErrorCode.FUEL_PRICE_PROVIDER_UNAVAILABLE
                    && exception.getErrorCode() != ErrorCode.FUEL_PRICE_PROVIDER_NOT_CONFIGURED) {
                throw exception;
            }
            if (request.manualFuelPrice() == null) {
                throw new CustomException(ErrorCode.FUEL_PRICE_INPUT_REQUIRED, exception);
            }
            validateManualFuelPrice(request.manualFuelPrice());
            return new FuelPriceSnapshot(
                    "USER_INPUT",
                    request.manualFuelPrice(),
                    null,
                    LocalDateTime.now(),
                    CarpoolFuelPriceSource.USER_INPUT);
        }
    }

    private void validateManualRoute(BigDecimal distanceKm, Integer tollFee) {
        if (distanceKm == null
                || tollFee == null
                || distanceKm.compareTo(new BigDecimal(CarpoolLimits.MIN_MANUAL_DISTANCE_KM)) < 0
                || distanceKm.compareTo(new BigDecimal(CarpoolLimits.MAX_MANUAL_DISTANCE_KM)) > 0
                || tollFee < CarpoolLimits.MIN_MANUAL_TOLL_FEE
                || tollFee > CarpoolLimits.MAX_MANUAL_TOLL_FEE) {
            throw new CustomException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
    }

    private void validateManualFuelPrice(BigDecimal fuelPrice) {
        if (fuelPrice.compareTo(new BigDecimal(CarpoolLimits.MIN_MANUAL_FUEL_PRICE)) < 0
                || fuelPrice.compareTo(new BigDecimal(CarpoolLimits.MAX_MANUAL_FUEL_PRICE)) > 0) {
            throw new CustomException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
    }

    private Resort findResort(Long resortId) {
        return resortRepository
                .findById(resortId)
                .filter(Resort::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CARPOOL_VALUE));
    }

    private PlaceCoordinate findDestination(Resort resort) {
        if (resort.getRouteLatitude() == null || resort.getRouteLongitude() == null) {
            throw new CustomException(ErrorCode.ROUTE_CALCULATION_FAILED);
        }
        return new PlaceCoordinate(
                resort.getName(),
                resort.getRegionName(),
                resort.getRegionName(),
                resort.getRouteLongitude(),
                resort.getRouteLatitude());
    }

    private String toKakaoFuel(CarpoolFuelType fuelType) {
        return switch (fuelType) {
            case GASOLINE, HYBRID_GASOLINE -> "GASOLINE";
            case DIESEL -> "DIESEL";
            case LPG -> "LPG";
        };
    }

    private record ResolvedRoute(
            BigDecimal distanceKm,
            int tollFee,
            int durationSeconds,
            CarpoolRouteSource source,
            LocalDateTime calculatedAt) {}
}
