package com.ikae.snowthing.domain.moderation.api;

import java.util.List;
import java.util.Map;

/** 2단계 판정 API. 입력은 제목과 본문뿐이다. 작성자, IP, 연락처 칸은 넣지 않는다. 테스트는 이 인터페이스를 가짜로 바꿔 임계값 분기를 본다. */
public interface ModerationApiClient {

    /**
     * 여러 글을 한 요청으로 보내고 입력 순서대로 카테고리별 점수를 돌려준다.
     *
     * @throws ModerationApiException 호출이나 응답 해석에 실패했을 때. 메시지에 본문이나 키를 넣지 않는다
     */
    List<Map<String, Double>> moderate(List<String> inputs);
}
