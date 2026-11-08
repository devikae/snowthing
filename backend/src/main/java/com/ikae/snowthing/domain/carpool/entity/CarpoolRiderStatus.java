package com.ikae.snowthing.domain.carpool.entity;

/** 탑승 확인 상태. 결정은 한 번이라 REQUESTED에서만 바뀐다. */
public enum CarpoolRiderStatus {
    REQUESTED,
    CONFIRMED,
    REJECTED
}
