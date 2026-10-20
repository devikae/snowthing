package com.ikae.snowthing.domain.chat.bot;

public interface BotGateway {

    BotAnswerResponse answer(BotAnswerRequest request);
}
