package com.ikae.snowthing.domain.resortweather.controller;

import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.resortweather.dto.ResortWeatherResponse;
import com.ikae.snowthing.domain.resortweather.service.ResortWeatherService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/resort-cams")
@RequiredArgsConstructor
public class ResortWeatherController {

    private static final Pattern RESORT_CODE = Pattern.compile("[A-Z0-9_]{1,40}");
    private static final CacheControl WEATHER_CACHE =
            CacheControl.maxAge(Duration.ofMinutes(2))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofMinutes(5));

    private final ResortWeatherService resortWeatherService;

    /** 공개 API. 값이 없으면 204로 응답하고 프론트는 기온 칩을 숨긴다. */
    @GetMapping("/{resortCode}/weather")
    public ResponseEntity<ResortWeatherResponse> getWeather(@PathVariable String resortCode) {
        if (!RESORT_CODE.matcher(resortCode).matches()) {
            throw new CustomException(ErrorCode.RESORT_NOT_FOUND);
        }
        return resortWeatherService
                .getWeather(resortCode)
                .map(weather -> ResponseEntity.ok().cacheControl(WEATHER_CACHE).body(weather))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
