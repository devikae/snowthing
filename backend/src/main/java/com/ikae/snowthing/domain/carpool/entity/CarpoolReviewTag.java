package com.ikae.snowthing.domain.carpool.entity;

/** 좋았어요 후기에만 붙는 좋았던 점. driverOnly 태그는 대상이 운전자일 때만 고를 수 있다. */
public enum CarpoolReviewTag {
    ON_TIME(false),
    KIND(false),
    FAIR_SETTLEMENT(false),
    SAFE_DRIVING(true),
    CLEAN_CAR(true);

    private final boolean driverOnly;

    CarpoolReviewTag(boolean driverOnly) {
        this.driverOnly = driverOnly;
    }

    public boolean isDriverOnly() {
        return driverOnly;
    }
}
