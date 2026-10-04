package com.ikae.snowthing.domain.carpool.external;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import tools.jackson.databind.JsonNode;

@Component
public class KakaoMobilityClient {

    private static final String AUTHORIZATION_PREFIX = "KakaoAK ";
    private static final int PLACE_RESULT_LIMIT = 5;
    private static final BigDecimal METERS_PER_KILOMETER = BigDecimal.valueOf(1000);
    private static final Duration PLACE_CACHE_DURATION = Duration.ofHours(24);

    private final CarpoolExternalApiProperties properties;
    private final RestClient localClient;
    private final RestClient navigationClient;
    private final Cache<String, List<PlaceCoordinate>> placeCache =
            Caffeine.newBuilder().expireAfterWrite(PLACE_CACHE_DURATION).maximumSize(500).build();

    @Autowired
    public KakaoMobilityClient(CarpoolExternalApiProperties properties) {
        JdkClientHttpRequestFactory requestFactory = createRequestFactory(properties);
        this.properties = properties;
        this.localClient = createClient("https://dapi.kakao.com", requestFactory);
        this.navigationClient = createClient("https://apis-navi.kakaomobility.com", requestFactory);
    }

    KakaoMobilityClient(
            CarpoolExternalApiProperties properties,
            RestClient localClient,
            RestClient navigationClient) {
        this.properties = properties;
        this.localClient = localClient;
        this.navigationClient = navigationClient;
    }

    public List<PlaceCoordinate> searchPlaces(String query) {
        requireApiKey();
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
        return placeCache.get(normalizedQuery, this::requestPlaces);
    }

    public RouteCalculation calculateRoute(
            BigDecimal originLongitude,
            BigDecimal originLatitude,
            PlaceCoordinate destination,
            String fuelCode) {
        requireApiKey();
        try {
            JsonNode response =
                    navigationClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/v1/directions")
                                                    .queryParam(
                                                            "origin",
                                                            coordinate(
                                                                    originLongitude,
                                                                    originLatitude))
                                                    .queryParam(
                                                            "destination",
                                                            coordinate(
                                                                    destination.longitude(),
                                                                    destination.latitude()))
                                                    .queryParam("priority", "RECOMMEND")
                                                    .queryParam("car_fuel", fuelCode)
                                                    .queryParam("summary", true)
                                                    .build())
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    AUTHORIZATION_PREFIX + properties.kakao().restApiKey())
                            .retrieve()
                            .body(JsonNode.class);
            JsonNode route = requiredArrayFirst(response, "routes");
            if (route.path("result_code").asInt(-1) != 0) {
                throw new CustomException(ErrorCode.ROUTE_CALCULATION_FAILED);
            }
            JsonNode summary = route.path("summary");
            int distanceMeters = summary.path("distance").asInt(-1);
            int tollFee = summary.path("fare").path("toll").asInt(-1);
            int durationSeconds = summary.path("duration").asInt(-1);
            if (distanceMeters <= 0 || tollFee < 0 || durationSeconds < 0) {
                throw new CustomException(ErrorCode.ROUTE_CALCULATION_FAILED);
            }
            return new RouteCalculation(
                    BigDecimal.valueOf(distanceMeters)
                            .divide(METERS_PER_KILOMETER)
                            .stripTrailingZeros(),
                    tollFee,
                    durationSeconds,
                    LocalDateTime.now());
        } catch (CustomException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.ROUTE_CALCULATION_FAILED, exception);
        }
    }

    private List<PlaceCoordinate> requestPlaces(String query) {
        try {
            JsonNode response =
                    localClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("/v2/local/search/keyword.json")
                                                    .queryParam("query", query)
                                                    .queryParam("size", PLACE_RESULT_LIMIT)
                                                    .build())
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    AUTHORIZATION_PREFIX + properties.kakao().restApiKey())
                            .retrieve()
                            .body(JsonNode.class);
            JsonNode documents = response == null ? null : response.path("documents");
            if (documents == null || !documents.isArray()) {
                throw new CustomException(ErrorCode.ROUTE_CALCULATION_FAILED);
            }
            return java.util.stream.StreamSupport.stream(documents.spliterator(), false)
                    .map(
                            document ->
                                    new PlaceCoordinate(
                                            document.path("place_name").asText(),
                                            document.path("address_name").asText(),
                                            document.path("road_address_name").asText(),
                                            decimal(document, "x"),
                                            decimal(document, "y")))
                    .filter(place -> place.longitude() != null && place.latitude() != null)
                    .toList();
        } catch (CustomException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.ROUTE_CALCULATION_FAILED, exception);
        }
    }

    private void requireApiKey() {
        if (!properties.hasKakaoKey()) {
            throw new CustomException(ErrorCode.ROUTE_PROVIDER_NOT_CONFIGURED);
        }
    }

    private JsonNode requiredArrayFirst(JsonNode response, String fieldName) {
        JsonNode array = response == null ? null : response.path(fieldName);
        if (array == null || !array.isArray() || array.isEmpty()) {
            throw new CustomException(ErrorCode.ROUTE_CALCULATION_FAILED);
        }
        return array.get(0);
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText();
        return value.isBlank() ? null : new BigDecimal(value);
    }

    private String coordinate(BigDecimal longitude, BigDecimal latitude) {
        return longitude.toPlainString() + "," + latitude.toPlainString();
    }

    private JdkClientHttpRequestFactory createRequestFactory(
            CarpoolExternalApiProperties apiProperties) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(apiProperties.effectiveConnectTimeoutMillis()))
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(
                Duration.ofMillis(apiProperties.effectiveReadTimeoutMillis()));
        return requestFactory;
    }

    private RestClient createClient(String baseUrl, JdkClientHttpRequestFactory requestFactory) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
