package com.ikae.snowthing.domain.carpool.dto;

import java.util.List;

import com.ikae.snowthing.domain.carpool.entity.CarpoolRiderStatus;
import com.ikae.snowthing.domain.member.entity.Member;

/**
 * 카풀 상세의 "함께 탄 사람" 영역.
 *
 * <p>members는 운전자와 확정된 탑승자에게만 채운다. REQUESTED 행은 운전자에게만 보인다.
 */
public record CarpoolParticipationResponse(
        String myStatus,
        boolean departed,
        boolean reviewOpen,
        long confirmedCount,
        int passengerCapacity,
        List<Participant> members) {

    public static final String DRIVER = "DRIVER";
    public static final String RIDER = "RIDER";

    public record Participant(
            String publicId,
            String nickname,
            String profileImageUrl,
            String role,
            CarpoolRiderStatus status,
            boolean reviewedByMe) {

        public static Participant of(
                Member member, String role, CarpoolRiderStatus status, boolean reviewedByMe) {
            boolean withdrawn = member.isWithdrawn();
            return new Participant(
                    withdrawn ? null : member.getPublicId(),
                    withdrawn ? Member.WITHDRAWN_NICKNAME : member.getNickname(),
                    withdrawn ? null : member.getProfileImageUrl(),
                    role,
                    status,
                    reviewedByMe);
        }
    }
}
