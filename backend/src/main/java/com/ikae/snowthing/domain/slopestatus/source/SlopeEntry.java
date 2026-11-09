package com.ikae.snowthing.domain.slopestatus.source;

/**
 * 공식 현황표의 슬로프 한 줄. 출처에 없는 항목은 null이다.
 *
 * @param difficulty 출처 표기 그대로(초급, 초/중급 등)
 * @param day 주간 운영 여부. 출처가 주간 칸을 주지 않으면 null
 * @param night 야간 운영 여부
 * @param lateNight 심야(백야) 운영 여부
 * @param note 비고나 운영 시간처럼 출처가 함께 적은 짧은 문구
 */
public record SlopeEntry(
        String name,
        String difficulty,
        SessionStatus day,
        SessionStatus night,
        SessionStatus lateNight,
        String note) {

    /** 어느 시간대든 하나라도 열려 있으면 운영 중으로 센다. 이름을 isXxx로 두지 않는다. 저장 JSON에 계산 값이 속성으로 섞이지 않게 하기 위해서다. */
    public boolean hasOpenSession() {
        return day == SessionStatus.OPEN
                || night == SessionStatus.OPEN
                || lateNight == SessionStatus.OPEN;
    }
}
