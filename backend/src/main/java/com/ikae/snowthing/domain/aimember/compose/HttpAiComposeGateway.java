package com.ikae.snowthing.domain.aimember.compose;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ikae.snowthing.domain.aimember.AiMemberConfig;
import com.ikae.snowthing.global.config.BotProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = AiMemberConfig.ENABLED_PROPERTY, havingValue = "true")
public class HttpAiComposeGateway implements AiComposeGateway {

    private static final String INTERNAL_SECRET_HEADER = "X-Snowthing-Internal-Secret";

    private final RestClient botRestClient;
    private final BotProperties properties;

    @Override
    public ComposeResponse compose(ComposeRequest request) {
        ComposeResponse response =
                botRestClient
                        .post()
                        .uri("/internal/v1/ai-members/compose")
                        .header(INTERNAL_SECRET_HEADER, properties.internalSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(ComposeResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty compose response");
        }
        return response;
    }
}
