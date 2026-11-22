package com.ikae.snowthing.domain.moderation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ikae.snowthing.domain.moderation.config.ModerationProperties;

/** OpenAI Moderation API 호출 형식과 응답 해석 (IKA-80). 실제 API는 부르지 않는다. */
class OpenAiModerationClientTest {

    private static final String BASE = "https://api.example.test/v1";

    private final ModerationProperties.Api api =
            new ModerationProperties.Api(true, "sk-test", BASE, null, 0, 0, null, 0, null, null);

    @Test
    @DisplayName("모델과 입력 배열만 보내고 결과를 입력 순서대로 읽는다")
    void sendsBatchAndParsesScores() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(BASE)
                        .defaultHeader("Authorization", "Bearer " + api.apiKey());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/moderations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(
                        content()
                                .json(
                                        "{\"model\":\"omni-moderation-latest\","
                                                + "\"input\":[\"첫 글\",\"둘째 글\"]}"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"modr-1\",\"results\":["
                                        + "{\"flagged\":false,\"category_scores\":"
                                        + "{\"sexual\":0.01,\"illicit\":0.2}},"
                                        + "{\"flagged\":true,\"category_scores\":"
                                        + "{\"sexual\":0.97,\"illicit\":0.0}}]}",
                                MediaType.APPLICATION_JSON));

        OpenAiModerationClient client = new OpenAiModerationClient(api, builder.build());
        List<Map<String, Double>> scores = client.moderate(List.of("첫 글", "둘째 글"));

        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).containsEntry("illicit", 0.2);
        assertThat(scores.get(1)).containsEntry("sexual", 0.97);
        server.verify();
    }

    @Test
    @DisplayName("서버 오류는 상태 코드만 담은 예외로 바꾼다")
    void serverErrorBecomesException() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/moderations")).andRespond(withServerError());

        OpenAiModerationClient client = new OpenAiModerationClient(api, builder.build());

        assertThatThrownBy(() -> client.moderate(List.of("글")))
                .isInstanceOf(ModerationApiException.class)
                .hasMessage("moderation request failed: HTTP 500");
    }

    @Test
    @DisplayName("결과 수가 입력 수와 다르면 실패로 본다")
    void sizeMismatch() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/moderations"))
                .andRespond(
                        withSuccess(
                                "{\"results\":[{\"category_scores\":{\"sexual\":0.1}}]}",
                                MediaType.APPLICATION_JSON));

        OpenAiModerationClient client = new OpenAiModerationClient(api, builder.build());

        assertThatThrownBy(() -> client.moderate(List.of("가", "나")))
                .isInstanceOf(ModerationApiException.class);
    }

    @Test
    @DisplayName("키가 없으면 부르지 않고, 설정 문자열에 키가 찍히지 않는다")
    void noKeyNoCall() {
        ModerationProperties.Api noKey =
                new ModerationProperties.Api(true, "", BASE, null, 0, 0, null, 0, null, null);
        assertThat(noKey.active()).isFalse();
        assertThatThrownBy(() -> new OpenAiModerationClient(noKey).moderate(List.of("글")))
                .isInstanceOf(ModerationApiException.class);
        assertThat(api.toString()).doesNotContain("sk-test");
    }
}
