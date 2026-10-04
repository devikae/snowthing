package com.ikae.snowthing.domain.carpool.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class KakaoMobilityClientContractTest {

    private static final String TEST_API_KEY = "kakao-contract-test-key";
    private static final String AUTHORIZATION_VALUE = "KakaoAK " + TEST_API_KEY;

    @Test
    void parsesOfficialPlaceAndDirectionsExamplesAndSendsExpectedRequestContract()
            throws Exception {
        RestClient.Builder localBuilder = RestClient.builder().baseUrl("https://dapi.kakao.com");
        MockRestServiceServer localServer = MockRestServiceServer.bindTo(localBuilder).build();
        RestClient.Builder navigationBuilder =
                RestClient.builder().baseUrl("https://apis-navi.kakaomobility.com");
        MockRestServiceServer navigationServer =
                MockRestServiceServer.bindTo(navigationBuilder).build();
        KakaoMobilityClient client =
                new KakaoMobilityClient(
                        properties(), localBuilder.build(), navigationBuilder.build());

        localServer
                .expect(once(), request -> assertPlaceSearchRequest(request.getURI().toString()))
                .andExpect(header("Authorization", AUTHORIZATION_VALUE))
                .andRespond(
                        withSuccess(
                                fixture("kakao-place-search-success.json"),
                                MediaType.APPLICATION_JSON));
        navigationServer
                .expect(once(), request -> assertDirectionsRequest(request.getURI().toString()))
                .andExpect(header("Authorization", AUTHORIZATION_VALUE))
                .andRespond(
                        withSuccess(
                                fixture("kakao-directions-success.json"),
                                MediaType.APPLICATION_JSON));

        List<PlaceCoordinate> firstSearch = client.searchPlaces("천호역");
        List<PlaceCoordinate> cachedSearch = client.searchPlaces("천호역");
        RouteCalculation route =
                client.calculateRoute(
                        firstSearch.getFirst().longitude(),
                        firstSearch.getFirst().latitude(),
                        new PlaceCoordinate(
                                "휘닉스파크",
                                "강원 평창군 봉평면",
                                "강원 평창군 태기로 174",
                                new BigDecimal("128.3277777"),
                                new BigDecimal("37.5805555")),
                        "GASOLINE");

        assertThat(firstSearch).hasSize(1).isEqualTo(cachedSearch);
        assertThat(firstSearch.getFirst().name()).isEqualTo("천호역 5호선");
        assertThat(firstSearch.getFirst().longitude()).isEqualByComparingTo("127.1234567");
        assertThat(firstSearch.getFirst().latitude()).isEqualByComparingTo("37.5387654");
        assertThat(route.distanceKm()).isEqualByComparingTo("190");
        assertThat(route.tollFee()).isEqualTo(12000);
        assertThat(route.durationSeconds()).isEqualTo(10800);
        localServer.verify();
        navigationServer.verify();
    }

    private void assertPlaceSearchRequest(String uri) {
        var queryParameters = UriComponentsBuilder.fromUriString(uri).build().getQueryParams();
        assertThat(uri).contains("/v2/local/search/keyword.json");
        assertThat(URLDecoder.decode(queryParameters.getFirst("query"), StandardCharsets.UTF_8))
                .isEqualTo("천호역");
        assertThat(queryParameters.getFirst("size")).isEqualTo("5");
    }

    private void assertDirectionsRequest(String uri) {
        var queryParameters = UriComponentsBuilder.fromUriString(uri).build().getQueryParams();
        assertThat(uri).contains("/v1/directions");
        assertThat(queryParameters.getFirst("origin")).isEqualTo("127.1234567,37.5387654");
        assertThat(queryParameters.getFirst("destination")).isEqualTo("128.3277777,37.5805555");
        assertThat(queryParameters.getFirst("priority")).isEqualTo("RECOMMEND");
        assertThat(queryParameters.getFirst("car_fuel")).isEqualTo("GASOLINE");
        assertThat(queryParameters.getFirst("summary")).isEqualTo("true");
    }

    private CarpoolExternalApiProperties properties() {
        return new CarpoolExternalApiProperties(
                new CarpoolExternalApiProperties.Kakao(TEST_API_KEY),
                new CarpoolExternalApiProperties.Opinet("opinet-contract-test-key"),
                1000,
                1000);
    }

    private String fixture(String filename) throws Exception {
        return new ClassPathResource("fixtures/carpool/" + filename)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
