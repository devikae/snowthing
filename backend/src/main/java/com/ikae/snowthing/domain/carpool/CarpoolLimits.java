package com.ikae.snowthing.domain.carpool;

public final class CarpoolLimits {

    public static final int MAX_CONTENT_LENGTH = 10_000;
    public static final int MAX_PASSENGER_CAPACITY = 20;
    public static final String MAX_FUEL_EFFICIENCY = "100.00";
    public static final String MAX_MANUAL_COST_PER_PERSON = "1000000";
    public static final String MIN_MANUAL_FUEL_PRICE = "1000";
    public static final String MAX_MANUAL_FUEL_PRICE = "3000";
    public static final String MIN_MANUAL_DISTANCE_KM = "1";
    public static final String MAX_MANUAL_DISTANCE_KM = "2000";
    public static final int MIN_MANUAL_TOLL_FEE = 0;
    public static final int MAX_MANUAL_TOLL_FEE = 20_000;
    public static final int MIN_PLACE_QUERY_LENGTH = 2;
    public static final int MAX_PLACE_QUERY_LENGTH = 100;

    private CarpoolLimits() {}
}
