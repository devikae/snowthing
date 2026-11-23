package com.ikae.snowthing.domain.admin.dto;

/** 관리자 대시보드 회원 현황과 운영 스위치. */
public record AdminDashboardStatsResponse(
        long totalMembers,
        long todayNewMembers,
        boolean chatEnabled,
        boolean anonymousChatEnabled,
        boolean marketGuestAccessEnabled,
        AiMemberSwitches aiMembers) {

    /**
     * @param installed 환경 변수로 AI 회원 기능이 켜져 있는지. 꺼져 있으면 아래 스위치는 효과가 없다.
     */
    public record AiMemberSwitches(
            boolean installed, boolean postEnabled, boolean commentEnabled, boolean chatEnabled) {}
}
