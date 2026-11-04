package com.ikae.snowthing.domain.search.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 실제 외부 API를 부르지 않는다. MockRestServiceServer로 요청 형식과 응답 해석만 확인한다. */
class HttpEmbeddingProviderContractTest {

    private static final String BASE_URL = "https://embeddings.example.invalid/v1";
    private static final String KEY = "test-key-not-real";

    @Test
    @DisplayName("OpenAI 호환 요청을 보내고, index 순서로 벡터를 맞추며, 사용 토큰을 비용 메타데이터로 돌려준다")
    void sendsRequestAndParsesResponse() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(BASE_URL)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + KEY);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(BASE_URL + "/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + KEY))
                .andExpect(
                        content()
                                .json(
                                        "{\"model\":\"m-small\",\"input\":[\"첫 문장\",\"둘째\"],"
                                                + "\"encoding_format\":\"float\"}"))
                .andRespond(
                        withSuccess(
                                "{\"data\":[{\"index\":1,\"embedding\":[0.5,0.25,0]},"
                                        + "{\"index\":0,\"embedding\":[1,0,-1]}],"
                                        + "\"usage\":{\"prompt_tokens\":7,\"total_tokens\":7}}",
                                MediaType.APPLICATION_JSON));
        HttpEmbeddingProvider provider = new HttpEmbeddingProvider(http(3), builder.build());

        EmbeddingResult result = provider.embed(List.of("첫 문장", "둘째"));

        server.verify();
        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().get(0)).containsExactly(1f, 0f, -1f);
        assertThat(result.vectors().get(1)).containsExactly(0.5f, 0.25f, 0f);
        assertThat(result.inputTokens()).isEqualTo(7L);
        assertThat(provider.modelId()).isEqualTo("http:m-small:d3");
    }

    @Test
    @DisplayName("차원이 다르거나 서버 오류면 키와 응답 본문 없이 실패한다")
    void rejectsInvalidResponses() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/embeddings"))
                .andRespond(
                        withSuccess(
                                "{\"data\":[{\"index\":0,\"embedding\":[1,2]}]}",
                                MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/embeddings"))
                .andRespond(withServerError().body("secret-echo " + KEY));
        HttpEmbeddingProvider provider = new HttpEmbeddingProvider(http(3), builder.build());

        assertThatThrownBy(() -> provider.embed(List.of("a")))
                .isInstanceOf(EmbeddingProviderException.class);
        assertThatThrownBy(() -> provider.embed(List.of("a")))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessageNotContaining(KEY)
                .hasMessageNotContaining("secret-echo");
        assertThatThrownBy(() -> provider.embed(List.of()))
                .isInstanceOf(EmbeddingProviderException.class);
    }

    @Test
    @DisplayName("주소, 모델, 차원, 키가 없거나 https가 아니면 빈을 만들지 못한다")
    void validatesConfiguration() {
        assertThatThrownBy(
                        () ->
                                new HttpEmbeddingProvider(
                                        new SemanticSearchProperties.Http(
                                                BASE_URL, "m", 3, "", 0, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(KEY);
        assertThatThrownBy(
                        () ->
                                new HttpEmbeddingProvider(
                                        new SemanticSearchProperties.Http(
                                                "http://embeddings.example.invalid",
                                                "m",
                                                3,
                                                KEY,
                                                0,
                                                0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(KEY);
        assertThatThrownBy(
                        () ->
                                new HttpEmbeddingProvider(
                                        new SemanticSearchProperties.Http(
                                                BASE_URL, "m", 0, KEY, 0, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static SemanticSearchProperties.Http http(int dimension) {
        return new SemanticSearchProperties.Http(BASE_URL, "m-small", dimension, KEY, 0, 0);
    }
}
