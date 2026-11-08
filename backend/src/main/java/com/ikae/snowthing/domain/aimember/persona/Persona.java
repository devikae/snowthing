package com.ikae.snowthing.domain.aimember.persona;

import java.util.List;
import java.util.Map;

/**
 * AI 멤버 캐릭터 하나.
 *
 * @param key compose 요청에서 쓰는 짧은 식별자. 닉네임 대신 보낸다.
 * @param writerIp 익명 글에 저장할 고정 가상 IP. 화면에는 앞 두 자리만 보인다.
 * @param description 성격, 사고방식, 말투, 관심 소재
 * @param activity 활동 시간, 빈도, 게시판 비중, 소재
 */
public record Persona(
        String key,
        String email,
        String nickname,
        String writerIp,
        String description,
        Activity activity) {

    public Persona(String key, String email, String nickname, String writerIp, String description) {
        this(key, email, nickname, writerIp, description, Activity.DEFAULT);
    }

    /** 시작 시각 이상 끝 시각 미만. 끝은 24까지 쓴다. */
    public record Window(int startHour, int endHour) {}

    /**
     * @param windows 활동 시간대
     * @param postsPerDay 하루 평균 글 수. 0이면 글을 쓰지 않는다.
     * @param boards 게시판 코드별 비중
     * @param topics 글감. 비어 있으면 모델이 고른다.
     */
    public record Activity(
            List<Window> windows,
            double postsPerDay,
            Map<String, Integer> boards,
            List<String> topics) {

        /** 페르소나 파일에 값이 없을 때 쓰는 값. 처음 4명 시절의 동작과 같다. */
        public static final Activity DEFAULT =
                new Activity(
                        List.of(new Window(7, 9), new Window(12, 13), new Window(18, 24)),
                        2.5,
                        Map.of("FREE", 1, "ANONYMOUS", 1, "QNA", 1),
                        List.of());

        public boolean activeAt(int hour) {
            return windows.stream()
                    .anyMatch(window -> hour >= window.startHour() && hour < window.endHour());
        }
    }
}
