package com.ikae.snowthing.domain.search.semantic;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

/**
 * OpenAI 호환 embeddings 엔드포인트 호출 ({@code POST {baseUrl}/embeddings}, 본문 {@code {"model", "input": [...]}},
 * 응답 {@code data[].embedding}, {@code usage.prompt_tokens}).
 *
 * <p>호출마다 비용이 생길 수 있다. {@code snowthing.search.semantic.enabled=true}와 {@code provider=http}를 함께 설정해야만
 * 쓰인다. 키는 환경 변수로만 받고 로그, 예외 메시지, toString에 넣지 않는다. 리디렉션은 따라가지 않는다(키가 다른 호스트로 가지 않게).
 */
public class HttpEmbeddingProvider implements EmbeddingProvider {

    static final int MAX_INPUTS_PER_CALL = 256;

    private final RestClient client;
    private final String model;
    private final int dimension;

    public HttpEmbeddingProvider(SemanticSearchProperties.Http http) {
        this(http, createClient(validate(http)));
    }

    HttpEmbeddingProvider(SemanticSearchProperties.Http http, RestClient client) {
        validate(http);
        this.client = client;
        this.model = http.model().strip();
        this.dimension = http.dimension();
    }

    private static SemanticSearchProperties.Http validate(SemanticSearchProperties.Http http) {
        if (http == null
                || http.baseUrl() == null
                || http.baseUrl().isBlank()
                || http.model() == null
                || http.model().isBlank()
                || http.dimension() < 1
                || http.dimension() > 4096
                || !http.hasApiKey()) {
            throw new IllegalStateException(
                    "snowthing.search.semantic.http requires base-url, model, dimension (1~4096)"
                            + " and SNOWTHING_SEARCH_EMBEDDING_API_KEY");
        }
        URI uri = URI.create(http.baseUrl().strip());
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!"https".equals(uri.getScheme()) && !local) {
            throw new IllegalStateException("snowthing.search.semantic.http.base-url must be https");
        }
        return http;
    }

    private static RestClient createClient(SemanticSearchProperties.Http http) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(http.effectiveConnectTimeoutMillis()))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(http.effectiveReadTimeoutMillis()));
        return RestClient.builder()
                .baseUrl(http.baseUrl().strip())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + http.apiKey().strip())
                .build();
    }

    @Override
    public String modelId() {
        return "http:" + model + ":d" + dimension;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public EmbeddingResult embed(List<String> texts) {
        if (texts.isEmpty() || texts.size() > MAX_INPUTS_PER_CALL) {
            throw new EmbeddingProviderException("input count out of range");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts);
        body.put("encoding_format", "float");
        JsonNode response;
        try {
            response =
                    client.post()
                            .uri("/embeddings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class);
        } catch (RestClientException e) {
            // 원인 메시지에는 응답 본문이 들어갈 수 있어 예외 종류만 남긴다.
            throw new EmbeddingProviderException(
                    "embedding request failed: " + e.getClass().getSimpleName());
        }
        return parse(response, texts.size());
    }

    private EmbeddingResult parse(JsonNode response, int expected) {
        if (response == null || !response.path("data").isArray()) {
            throw new EmbeddingProviderException("embedding response has no data array");
        }
        JsonNode data = response.path("data");
        if (data.size() != expected) {
            throw new EmbeddingProviderException("embedding response size mismatch");
        }
        float[][] ordered = new float[expected][];
        for (int i = 0; i < data.size(); i++) {
            JsonNode item = data.get(i);
            int index = item.path("index").isIntegralNumber() ? item.path("index").asInt() : i;
            JsonNode embedding = item.path("embedding");
            if (index < 0
                    || index >= expected
                    || ordered[index] != null
                    || !embedding.isArray()
                    || embedding.size() != dimension) {
                throw new EmbeddingProviderException("embedding response item is invalid");
            }
            float[] vector = new float[dimension];
            for (int j = 0; j < dimension; j++) {
                JsonNode value = embedding.get(j);
                if (!value.isNumber()) {
                    throw new EmbeddingProviderException("embedding value is not a number");
                }
                vector[j] = (float) value.asDouble();
            }
            if (!VectorMath.isFinite(vector)) {
                throw new EmbeddingProviderException("embedding value is not finite");
            }
            ordered[index] = vector;
        }
        JsonNode tokens = response.path("usage").path("prompt_tokens");
        Long inputTokens = tokens.isIntegralNumber() ? tokens.asLong() : null;
        return new EmbeddingResult(Arrays.asList(ordered), inputTokens);
    }
}
