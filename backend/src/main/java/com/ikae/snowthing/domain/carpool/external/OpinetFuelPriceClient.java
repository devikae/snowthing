package com.ikae.snowthing.domain.carpool.external;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelPriceSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpinetFuelPriceClient {

    private static final Duration CACHE_DURATION = Duration.ofHours(6);
    private static final DateTimeFormatter TRADE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Map<CarpoolFuelType, String> PRODUCT_CODES =
            Map.of(
                    CarpoolFuelType.GASOLINE, "B027",
                    CarpoolFuelType.HYBRID_GASOLINE, "B027",
                    CarpoolFuelType.DIESEL, "D047",
                    CarpoolFuelType.LPG, "K015");

    private final CarpoolExternalApiProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final Cache<String, FuelPriceSnapshot> cache =
            Caffeine.newBuilder().expireAfterWrite(CACHE_DURATION).maximumSize(10).build();

    @Autowired
    public OpinetFuelPriceClient(
            CarpoolExternalApiProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(properties.effectiveConnectTimeoutMillis()))
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.effectiveReadTimeoutMillis()));
        this.properties = properties;
        this.client =
                RestClient.builder()
                        .baseUrl("https://www.opinet.co.kr")
                        .requestFactory(requestFactory)
                        .build();
        this.objectMapper = objectMapper;
    }

    OpinetFuelPriceClient(CarpoolExternalApiProperties properties, RestClient client) {
        this(properties, client, new ObjectMapper());
    }

    OpinetFuelPriceClient(
            CarpoolExternalApiProperties properties, RestClient client, ObjectMapper objectMapper) {
        this.properties = properties;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public FuelPriceSnapshot getAveragePrice(CarpoolFuelType fuelType) {
        String productCode = PRODUCT_CODES.get(fuelType);
        if (productCode == null) {
            throw new CustomException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
        FuelPriceSnapshot cached = cache.getIfPresent(productCode);
        if (cached != null) {
            return cached.withSource(CarpoolFuelPriceSource.CACHE);
        }
        if (!properties.hasOpinetKey()) {
            throw new CustomException(ErrorCode.FUEL_PRICE_PROVIDER_NOT_CONFIGURED);
        }
        FuelPriceSnapshot requested = requestAveragePrice(productCode);
        cache.put(productCode, requested);
        return requested;
    }

    private FuelPriceSnapshot requestAveragePrice(String productCode) {
        try {
            String responseBody =
                    client.get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/api/avgAllPrice.do")
                                                    .queryParam("out", "json")
                                                    .queryParam(
                                                            "code", properties.opinet().apiKey())
                                                    .build())
                            .retrieve()
                            .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode oils = response == null ? null : response.path("RESULT").path("OIL");
            if (oils == null || !oils.isArray()) {
                throw new CustomException(ErrorCode.FUEL_PRICE_PROVIDER_UNAVAILABLE);
            }
            for (JsonNode oil : oils) {
                if (productCode.equals(oil.path("PRODCD").asText())) {
                    BigDecimal price = new BigDecimal(oil.path("PRICE").asText());
                    LocalDate tradeDate =
                            LocalDate.parse(oil.path("TRADE_DT").asText(), TRADE_DATE_FORMAT);
                    if (price.signum() <= 0) {
                        break;
                    }
                    return new FuelPriceSnapshot(
                            productCode, price, tradeDate, LocalDateTime.now());
                }
            }
            throw new CustomException(ErrorCode.FUEL_PRICE_PROVIDER_UNAVAILABLE);
        } catch (CustomException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.FUEL_PRICE_PROVIDER_UNAVAILABLE, exception);
        }
    }
}
