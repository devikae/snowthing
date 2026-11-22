package com.ikae.snowthing.domain.moderation.api;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.ikae.snowthing.domain.moderation.config.ModerationProperties;

import tools.jackson.databind.JsonNode;

/**
 * OpenAI Moderation API ({@code POST {baseUrl}/moderations}, 본문 {@code {"model", "input": [...]}},
 * 응답 {@code results[].category_scores}). 무료지만 속도 제한이 있다.
 *
 * <p>키는 환경 변수로만 받고 로그, 예외 메시지, toString에 넣지 않는다. 리디렉션은 따라가지 않는다(키가 다른 호스트로 가지 않게). 키가 없으면 호출하지
 * 않는다(처리기가 {@link ModerationProperties.Api#active()}로 먼저 확인한다).
 */
public class OpenAiModerationClient implements ModerationApiClient {

    private final ModerationProperties.Api api;
    private volatile RestClient client;

    public OpenAiModerationClient(ModerationProperties.Api api) {
        this.api = api;
    }

    /** 테스트에서 가짜 서버에 붙인 클라이언트를 넣는다. */
    OpenAiModerationClient(ModerationProperties.Api api, RestClient client) {
        this.api = api;
        this.client = client;
    }

    @Override
    public List<Map<String, Double>> moderate(List<String> inputs) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        if (!api.active()) {
            throw new ModerationApiException("moderation api is not configured");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", api.model());
        body.put("input", inputs);
        JsonNode response;
        try {
            response =
                    client().post()
                            .uri("/moderations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // 응답 본문에 입력 일부가 들어갈 수 있어 상태 코드만 남긴다.
            throw new ModerationApiException(
                    "moderation request failed: HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new ModerationApiException(
                    "moderation request failed: " + e.getClass().getSimpleName());
        }
        return parse(response, inputs.size());
    }

    static List<Map<String, Double>> parse(JsonNode response, int expected) {
        if (response == null || !response.path("results").isArray()) {
            throw new ModerationApiException("moderation response has no results");
        }
        JsonNode results = response.path("results");
        if (results.size() != expected) {
            throw new ModerationApiException("moderation response size mismatch");
        }
        List<Map<String, Double>> scores = new ArrayList<>(expected);
        for (JsonNode result : results) {
            JsonNode categoryScores = result.path("category_scores");
            if (!categoryScores.isObject() || categoryScores.isEmpty()) {
                throw new ModerationApiException("moderation result has no category_scores");
            }
            // 점수가 비었거나 숫자가 아니면 통과로 두지 않고 실패로 돌려 재시도와 검토 대기로 보낸다.
            Map<String, Double> row = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : categoryScores.properties()) {
                if (!entry.getValue().isNumber()) {
                    throw new ModerationApiException("moderation score is not a number");
                }
                row.put(entry.getKey(), entry.getValue().asDouble());
            }
            scores.add(row);
        }
        return scores;
    }

    private RestClient client() {
        RestClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    client = create();
                }
                current = client;
            }
        }
        return current;
    }

    private RestClient create() {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(api.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(api.readTimeout());
        return RestClient.builder()
                .baseUrl(api.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + api.apiKey())
                .build();
    }
}
