package com.ikae.snowthing.domain.member.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.comment.event.CommentCreatedEvent;
import com.ikae.snowthing.domain.member.entity.Member;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴할 때 회원 행 밖에 있는 개인정보를 한 트랜잭션에서 지운다. 어떤 테이블의 무엇을 지우는지 한눈에 보이게 SQL로 모았다. 표는
 * docs/conception/025_member_withdrawal/01_design.md에 있다.
 *
 * <p>호출자의 트랜잭션에 참여한다. 글과 댓글의 FK는 남겨 두고 표시 단계에서 {@link Member#isWithdrawn()}으로 가린다.
 */
@Repository
@RequiredArgsConstructor
public class MemberWithdrawalRepository {

    /** IPv4는 앞 두 자리만 남긴다. 익명 글 표시(1.2.***.***)가 그대로 유지된다. 나머지는 복원할 수 없는 값으로 바꾼다. */
    private static final String TRUNCATED_IP =
            "IF(writer_ip LIKE '%.%.%.%' AND writer_ip NOT LIKE '%:%',"
                    + " CONCAT(SUBSTRING_INDEX(writer_ip, '.', 2), '.0.0'), '0.0.0.0')";

    private static final List<String> BY_MEMBER =
            List.of(
                    "DELETE FROM member_social_account WHERE member_id = :memberId",
                    "DELETE FROM member_resort WHERE member_id = :memberId",
                    "DELETE FROM member_riding_style WHERE member_id = :memberId",
                    "DELETE FROM member_registration_consent WHERE member_id = :memberId",
                    "DELETE FROM member_marketing_consent_history WHERE member_id = :memberId",
                    "DELETE FROM member_block WHERE blocker_id = :memberId",
                    "DELETE FROM notification WHERE receiver_id = :memberId",
                    // 방문 기록은 언제 어디 있었는지 알려 주는 정보다. 공유 링크도 함께 닫는다.
                    "DELETE FROM season_share WHERE member_id = :memberId",
                    "DELETE FROM season_visit WHERE member_id = :memberId",
                    // 운전자가 아직 처리하지 않은 탑승 요청. 확정되거나 거절된 기록과 후기는 남긴다(027).
                    "DELETE FROM carpool_rider WHERE member_id = :memberId AND status = 'REQUESTED'",
                    "DELETE FROM post_scrap WHERE member_id = :memberId",
                    "UPDATE api_token SET revoked_at = :now"
                            + " WHERE member_id = :memberId AND revoked_at IS NULL",
                    // 연락처가 사라진 판매글이 판매 중으로 남으면 구매자가 막다른 길을 만나므로 함께 닫는다.
                    "UPDATE market_listing listing JOIN post p ON p.post_id = listing.post_id"
                            + " SET listing.contact = :withdrawnName, listing.trade_status = 'SOLD'"
                            + " WHERE p.member_id = :memberId",
                    "UPDATE carpool_detail detail JOIN post p ON p.post_id = detail.post_id"
                            + " SET detail.contact_info = NULL,"
                            + " detail.contact_public_to_guest = FALSE"
                            + " WHERE p.member_id = :memberId",
                    "UPDATE post SET writer_ip = " + TRUNCATED_IP + " WHERE member_id = :memberId",
                    "UPDATE comment SET writer_ip = "
                            + TRUNCATED_IP
                            + " WHERE member_id = :memberId",
                    "UPDATE post_reaction SET writer_ip = NULL WHERE member_id = :memberId");

    private final NamedParameterJdbcTemplate jdbc;

    public void erasePersonalData(
            long memberId,
            String nickname,
            String email,
            Collection<String> emails,
            String anonymizedEmail,
            LocalDateTime now) {
        MapSqlParameterSource byMember =
                new MapSqlParameterSource(
                        Map.of(
                                "memberId",
                                memberId,
                                "now",
                                now,
                                "withdrawnName",
                                Member.WITHDRAWN_NICKNAME));
        BY_MEMBER.forEach(sql -> jdbc.update(sql, byMember));

        jdbc.update(
                "DELETE FROM email_verification WHERE email IN (:emails)",
                Map.of("emails", emails));
        jdbc.update(
                "UPDATE post_image_upload SET owner_email = :anonymized WHERE owner_email = :email",
                Map.of("anonymized", anonymizedEmail, "email", email));
        // 알림은 작성 시점 닉네임을 값으로 저장한다. 닉네임이 "익명"인 회원이면 익명 댓글 알림까지 바뀌므로 건너뛴다.
        if (!CommentCreatedEvent.ANONYMOUS_NAME.equals(nickname)) {
            jdbc.update(
                    "UPDATE notification SET actor_name = :withdrawnName"
                            + " WHERE actor_name = :nickname",
                    Map.of("withdrawnName", Member.WITHDRAWN_NICKNAME, "nickname", nickname));
        }
    }
}
