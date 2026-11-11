package com.ikae.snowthing.domain.member.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * 첫 관리자 지정 (IKA-12). 앱이 준비되면 {@code snowthing.admin.bootstrap-emails}(환경 변수 {@code
 * SNOWTHING_ADMIN_BOOTSTRAP_EMAILS}, 쉼표 구분)에 있는 이메일의 활성 회원을 ROLE_ADMIN으로 올린다.
 *
 * <ul>
 *   <li>비어 있으면 아무것도 하지 않는다. 기본값은 비어 있음.
 *   <li>없는 이메일, 탈퇴나 정지 회원은 건너뛴다. 이미 관리자면 바꾸지 않는다(여러 번 기동해도 같다).
 *   <li>목록에서 이메일을 지워도 관리자 권한을 거두지 않는다. 권한 회수는 관리자 화면의 운영자 해제(IKA-77)로 명시적으로 한다. 설정 실수 한 번으로 운영자가
 *       갑자기 관리자 화면에서 밀려나지 않게 하려는 선택이다.
 *   <li>로그에는 바뀐 회원 ID와 건수만 남긴다. 이메일은 남기지 않는다.
 * </ul>
 */
@Slf4j
@Component
public class AdminBootstrap {

    private final NamedParameterJdbcTemplate jdbc;
    private final EmailNormalizer emailNormalizer;
    private final List<String> emails;

    public AdminBootstrap(
            NamedParameterJdbcTemplate jdbc,
            EmailNormalizer emailNormalizer,
            @Value("${snowthing.admin.bootstrap-emails:}") String rawEmails) {
        this.jdbc = jdbc;
        this.emailNormalizer = emailNormalizer;
        this.emails =
                Arrays.stream(rawEmails == null ? new String[0] : rawEmails.split(","))
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .map(emailNormalizer::normalize)
                        .distinct()
                        .toList();
    }

    // 같은 빈 안에서 promote()를 부르면 프록시를 거치지 않아 트랜잭션이 없다. 리스너 자체를 트랜잭션으로 묶는다.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        promote();
    }

    /**
     * @return 이번에 ROLE_ADMIN으로 바뀐 회원 수
     */
    @Transactional
    public int promote() {
        if (emails.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource params = new MapSqlParameterSource("emails", emails);
        List<Long> targets =
                jdbc.queryForList(
                        "SELECT member_id FROM member WHERE email IN (:emails)"
                                + " AND status = 'ACTIVE' AND role <> 'ROLE_ADMIN' FOR UPDATE",
                        params,
                        Long.class);
        if (targets.isEmpty()) {
            log.info("Admin bootstrap: no member changed");
            return 0;
        }
        int changed =
                jdbc.update(
                        "UPDATE member SET role = 'ROLE_ADMIN' WHERE member_id IN (:ids)"
                                + " AND status = 'ACTIVE' AND role <> 'ROLE_ADMIN'",
                        new MapSqlParameterSource("ids", targets));
        log.info("Admin bootstrap: promoted {} member(s), memberIds={}", changed, targets);
        return changed;
    }
}
