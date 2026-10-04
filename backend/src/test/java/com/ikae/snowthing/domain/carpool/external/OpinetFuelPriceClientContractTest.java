package com.ikae.snowthing.domain.carpool.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelPriceSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;

class OpinetFuelPriceClientContractTest {

    private static final String TEST_API_KEY = "opinet-contract-test-key";

    @Test
    void parsesNationalAverageExampleAndCachesSameFuelRequest() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.opinet.co.kr");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpinetFuelPriceClient client = new OpinetFuelPriceClient(properties(), builder.build());
        server.expect(once(), request -> assertAveragePriceRequest(request.getURI().toString()))
                .andRespond(
                        withSuccess(
                                fixture("opinet-national-average-success.json"),
                                MediaType.APPLICATION_JSON));

        FuelPriceSnapshot first = client.getAveragePrice(CarpoolFuelType.GASOLINE);
        FuelPriceSnapshot cached = client.getAveragePrice(CarpoolFuelType.GASOLINE);

        assertThat(first.source()).isEqualTo(CarpoolFuelPriceSource.OPINET);
        assertThat(cached.source()).isEqualTo(CarpoolFuelPriceSource.CACHE);
        assertThat(cached.pricePerLiter()).isEqualByComparingTo(first.pricePerLiter());
        assertThat(first.productCode()).isEqualTo("B027");
        assertThat(first.pricePerLiter()).isEqualByComparingTo("1858.12");
        assertThat(first.tradeDate()).hasToString("2026-10-03");
        server.verify();
    }

    private void assertAveragePriceRequest(String uri) {
        var queryParameters = UriComponentsBuilder.fromUriString(uri).build().getQueryParams();
        assertThat(uri).contains("/api/avgAllPrice.do");
        assertThat(queryParameters.getFirst("out")).isEqualTo("json");
        assertThat(queryParameters.getFirst("code")).isEqualTo(TEST_API_KEY);
    }

    private CarpoolExternalApiProperties properties() {
        return new CarpoolExternalApiProperties(
                new CarpoolExternalApiProperties.Kakao("kakao-contract-test-key"),
                new CarpoolExternalApiProperties.Opinet(TEST_API_KEY),
                1000,
                1000);
    }

    private String fixture(String filename) throws Exception {
        return new ClassPathResource("fixtures/carpool/" + filename)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
