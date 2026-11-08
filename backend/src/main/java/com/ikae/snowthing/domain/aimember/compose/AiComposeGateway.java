package com.ikae.snowthing.domain.aimember.compose;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 봇 서버의 {@code POST /internal/v1/ai-members/compose}. 문장만 만들고 저장은 하지 않는다. */
public interface AiComposeGateway {

    ComposeResponse compose(ComposeRequest request);

    record PersonaPayload(String key, String description) {}

    record Message(String speaker, String content) {}

    record PostThread(String title, String content, List<Message> comments) {}

    record Context(
            String today,
            String season,
            List<String> resorts,
            List<String> snowReports,
            List<String> recentTitles,
            List<String> topicKeywords) {}

    /**
     * action: POST, COMMENT, CHAT. board는 POST·COMMENT만, lineCount는 CHAT만 쓴다. 봇 서버 검증이 null을 받지
     * 않으므로 빼고 보낸다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ComposeRequest(
            String requestId,
            String action,
            List<PersonaPayload> personas,
            String board,
            PostThread thread,
            List<Message> recentChat,
            Integer lineCount,
            Context context,
            Brief brief) {}

    /** 이번 글의 소재(없으면 null), 형식, 길이. CHAT에는 쓰지 않는다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Brief(String topic, String form, String length) {}

    record Line(String speaker, String text) {}

    record ComposeResponse(String requestId, String title, String content, List<Line> lines) {}
}
