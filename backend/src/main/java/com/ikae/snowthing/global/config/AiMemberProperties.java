package com.ikae.snowthing.global.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 멤버 설정 (IKA-14). 꺼져 있으면 AI 멤버 빈이 하나도 만들어지지 않는다.
 *
 * @param personaFile 저장소 밖 페르소나 YAML 경로. 공개 저장소에 캐릭터 정의가 남지 않게 한다.
 * @param dailyCallLimit 하루 compose 호출 상한
 * @param topicKeywords 운영자가 넣는 선택 화제 키워드. 봇 서버 검증에 맞춰 50자 이하 10개까지만 쓴다.
 * @param firstOpeningDate 이번 시즌 국내 첫 개장일. 이 날 전에는 AI 멤버에 개장 전이라고 알린다. 시즌마다 바꾼다.
 */
@ConfigurationProperties(prefix = "snowthing.ai-members")
public record AiMemberProperties(
        boolean enabled,
        String personaFile,
        int dailyCallLimit,
        List<String> topicKeywords,
        LocalDate firstOpeningDate) {

    public AiMemberProperties {
        personaFile = personaFile != null ? personaFile : "";
        dailyCallLimit = dailyCallLimit > 0 ? dailyCallLimit : 100;
        topicKeywords =
                topicKeywords == null
                        ? List.of()
                        : topicKeywords.stream()
                                .map(String::strip)
                                .filter(k -> !k.isEmpty() && k.length() <= 50)
                                .limit(10)
                                .toList();
    }
}
