package com.ikae.snowthing.domain.resortweather.source;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 한 소스에서 읽은 기온. sky는 원본이 하늘 상태를 주지 않으면 null이다. */
public record WeatherReading(
        BigDecimal temperature, String sky, OffsetDateTime observedAt, WeatherSourceType source) {}
