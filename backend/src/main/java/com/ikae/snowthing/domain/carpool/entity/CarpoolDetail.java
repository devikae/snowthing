package com.ikae.snowthing.domain.carpool.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "carpool_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarpoolDetail extends BaseTimeEntity {

    @Id
    @Column(name = "post_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(name = "departure_region", nullable = false, length = 100)
    private String departureRegion;

    @Column(name = "meeting_place", nullable = false, length = 200)
    private String meetingPlace;

    @Column(name = "departure_latitude", precision = 10, scale = 7)
    private BigDecimal departureLatitude;

    @Column(name = "departure_longitude", precision = 10, scale = 7)
    private BigDecimal departureLongitude;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_resort_id", nullable = false)
    private Resort destinationResort;

    @Column(name = "destination_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLongitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false, length = 20)
    private CarpoolTripType tripType;

    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "return_at")
    private LocalDateTime returnAt;

    @Column(name = "passenger_capacity", nullable = false)
    private int passengerCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 30)
    private CarpoolFuelType fuelType;

    @Column(name = "fuel_efficiency", nullable = false, precision = 6, scale = 2)
    private BigDecimal fuelEfficiency;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_mode", nullable = false, length = 20)
    private CarpoolCostMode costMode;

    @Column(name = "fuel_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal fuelPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_price_source", nullable = false, length = 20)
    private CarpoolFuelPriceSource fuelPriceSource;

    @Column(name = "fuel_price_observed_at", nullable = false)
    private LocalDateTime fuelPriceObservedAt;

    @Column(name = "route_distance_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal routeDistanceKm;

    @Column(name = "route_toll_fee", nullable = false)
    private int routeTollFee;

    @Column(name = "estimated_fuel_cost", nullable = false)
    private int estimatedFuelCost;

    @Column(name = "estimated_total_cost", nullable = false)
    private int estimatedTotalCost;

    @Column(name = "estimated_cost_per_person", nullable = false)
    private int estimatedCostPerPerson;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_source", nullable = false, length = 20)
    private CarpoolRouteSource routeSource;

    @Column(name = "route_calculated_at")
    private LocalDateTime routeCalculatedAt;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Column(name = "contact_public_to_guest", nullable = false)
    private boolean contactPublicToGuest;

    @Column(name = "equipment_load_available", nullable = false)
    private boolean equipmentLoadAvailable;

    @Builder
    public CarpoolDetail(
            Post post,
            String departureRegion,
            String meetingPlace,
            BigDecimal departureLatitude,
            BigDecimal departureLongitude,
            Resort destinationResort,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude,
            CarpoolTripType tripType,
            LocalDateTime departureAt,
            LocalDateTime returnAt,
            int passengerCapacity,
            CarpoolFuelType fuelType,
            BigDecimal fuelEfficiency,
            CarpoolCostMode costMode,
            BigDecimal fuelPrice,
            CarpoolFuelPriceSource fuelPriceSource,
            LocalDateTime fuelPriceObservedAt,
            BigDecimal routeDistanceKm,
            int routeTollFee,
            int estimatedFuelCost,
            int estimatedTotalCost,
            int estimatedCostPerPerson,
            CarpoolRouteSource routeSource,
            LocalDateTime routeCalculatedAt,
            String contactInfo,
            boolean contactPublicToGuest,
            boolean equipmentLoadAvailable) {
        this.post = post;
        this.departureRegion = departureRegion;
        this.meetingPlace = meetingPlace;
        this.departureLatitude = departureLatitude;
        this.departureLongitude = departureLongitude;
        this.destinationResort = destinationResort;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.tripType = tripType;
        this.departureAt = departureAt;
        this.returnAt = returnAt;
        this.passengerCapacity = passengerCapacity;
        this.fuelType = fuelType;
        this.fuelEfficiency = fuelEfficiency;
        this.costMode = costMode;
        this.fuelPrice = fuelPrice;
        this.fuelPriceSource = fuelPriceSource;
        this.fuelPriceObservedAt = fuelPriceObservedAt;
        this.routeDistanceKm = routeDistanceKm;
        this.routeTollFee = routeTollFee;
        this.estimatedFuelCost = estimatedFuelCost;
        this.estimatedTotalCost = estimatedTotalCost;
        this.estimatedCostPerPerson = estimatedCostPerPerson;
        this.routeSource = routeSource;
        this.routeCalculatedAt = routeCalculatedAt;
        this.contactInfo = contactInfo;
        this.contactPublicToGuest = contactPublicToGuest;
        this.equipmentLoadAvailable = equipmentLoadAvailable;
    }

    public void update(
            String departureRegion,
            String meetingPlace,
            BigDecimal departureLatitude,
            BigDecimal departureLongitude,
            Resort destinationResort,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude,
            CarpoolTripType tripType,
            LocalDateTime departureAt,
            LocalDateTime returnAt,
            int passengerCapacity,
            CarpoolFuelType fuelType,
            BigDecimal fuelEfficiency,
            CarpoolCostMode costMode,
            BigDecimal fuelPrice,
            CarpoolFuelPriceSource fuelPriceSource,
            LocalDateTime fuelPriceObservedAt,
            BigDecimal routeDistanceKm,
            int routeTollFee,
            int estimatedFuelCost,
            int estimatedTotalCost,
            int estimatedCostPerPerson,
            CarpoolRouteSource routeSource,
            LocalDateTime routeCalculatedAt,
            String contactInfo,
            boolean contactPublicToGuest,
            boolean equipmentLoadAvailable) {
        this.departureRegion = departureRegion;
        this.meetingPlace = meetingPlace;
        this.departureLatitude = departureLatitude;
        this.departureLongitude = departureLongitude;
        this.destinationResort = destinationResort;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.tripType = tripType;
        this.departureAt = departureAt;
        this.returnAt = returnAt;
        this.passengerCapacity = passengerCapacity;
        this.fuelType = fuelType;
        this.fuelEfficiency = fuelEfficiency;
        this.costMode = costMode;
        this.fuelPrice = fuelPrice;
        this.fuelPriceSource = fuelPriceSource;
        this.fuelPriceObservedAt = fuelPriceObservedAt;
        this.routeDistanceKm = routeDistanceKm;
        this.routeTollFee = routeTollFee;
        this.estimatedFuelCost = estimatedFuelCost;
        this.estimatedTotalCost = estimatedTotalCost;
        this.estimatedCostPerPerson = estimatedCostPerPerson;
        this.routeSource = routeSource;
        this.routeCalculatedAt = routeCalculatedAt;
        this.contactInfo = contactInfo;
        this.contactPublicToGuest = contactPublicToGuest;
        this.equipmentLoadAvailable = equipmentLoadAvailable;
    }
}
