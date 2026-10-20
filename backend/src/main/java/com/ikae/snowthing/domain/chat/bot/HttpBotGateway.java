package com.ikae.snowthing.domain.chat.bot;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ikae.snowthing.global.config.BotProperties;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HttpBotGateway implements BotGateway {

    private static final String INTERNAL_SECRET_HEADER = "X-Snowthing-Internal-Secret";

    private final RestClient botRestClient;
    private final BotProperties properties;

    @Override
    public BotAnswerResponse answer(BotAnswerRequest request) {
        BotAnswerResponse response =
                botRestClient
                        .post()
                        .uri("/internal/v1/chat/answers")
                        .header(INTERNAL_SECRET_HEADER, properties.internalSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(BotAnswerResponse.class);
        if (response == null || response.answer() == null || response.answer().isBlank()) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return response;
    }
}
