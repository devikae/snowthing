package com.ikae.snowthing.domain.dmmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.admin.service.SystemConfigService;
import com.ikae.snowthing.domain.dmmail.DmMailTestConfig.SwitchableSender;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.message.dto.DirectMessageSendRequest;
import com.ikae.snowthing.domain.message.service.DirectMessageService;
import com.ikae.snowthing.domain.notification.service.NotificationSettingService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.testsupport.FakeSesServer.Mode;

/**
 * 쪽지 메일 스위치 테스트 공통 준비 (IKA-109). 화면 스위치는 실제 관리자 API로 바꾼다. 그래야 설정 캐시와 조치 기록까지 운영과 같은 경로를 탄다.
 *
 * <p>테스트 스키마는 create-drop이라 system_config 행이 없다. 운영 마이그레이션(061)이 넣는 두 행을 여기서 만든다.
 */
abstract class DmMailSwitchTestSupport {

    static final String ENQUEUE = SystemConfigService.KEY_DM_MAIL_ENQUEUE_ENABLED;
    static final String SEND = SystemConfigService.KEY_DM_MAIL_SEND_ENABLED;

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TransactionTemplate transactions;
    @Autowired protected MemberRepository members;
    @Autowired protected DirectMessageService directMessages;
    @Autowired protected NotificationSettingService settings;
    @Autowired protected SwitchableSender sender;

    protected final List<Long> memberIds = new ArrayList<>();
    protected Member admin;
    protected Member alice;

    protected void prepare() throws Exception {
        clearOutbox();
        DmMailTestConfig.FAKE_SES.reset(Mode.ACCEPT);
        sender.useFake();
        for (String key : List.of(ENQUEUE, SEND)) {
            jdbc.update(
                    "INSERT INTO system_config (config_key, config_value, value_type, description,"
                            + " updated_at, updated_by) VALUES (?, 'false', 'BOOLEAN', ?, NOW(6), 'SYSTEM')"
                            + " ON DUPLICATE KEY UPDATE config_value = 'false'",
                    key,
                    key);
        }
        admin = member("admin", Role.ROLE_ADMIN);
        alice = member("alice", Role.ROLE_USER);
        // 앞 테스트가 남긴 캐시 값을 지우려고 API로 한 번 더 끈다.
        setSwitch(ENQUEUE, false);
        setSwitch(SEND, false);
        jdbc.update("DELETE FROM admin_action_log WHERE actor_member_id = ?", admin.getId());
    }

    protected void cleanup() {
        clearOutbox();
        jdbc.update("DELETE FROM system_config WHERE config_key IN (?, ?)", ENQUEUE, SEND);
        for (Long id : memberIds) {
            jdbc.update("DELETE FROM admin_action_log WHERE actor_member_id = ?", id);
            jdbc.update(
                    "DELETE FROM direct_message WHERE sender_id = ? OR receiver_id = ?", id, id);
            jdbc.update("DELETE FROM member_notification_setting WHERE member_id = ?", id);
        }
        for (Long id : memberIds) {
            jdbc.update("DELETE FROM member WHERE member_id = ?", id);
        }
        memberIds.clear();
    }

    private void clearOutbox() {
        jdbc.update("DELETE FROM dm_mail_outbox");
        jdbc.update("DELETE FROM dm_mail_recipient_state");
    }

    /** 관리자 화면이 부르는 것과 같은 API로 스위치를 바꾼다. */
    protected void setSwitch(String key, boolean on) throws Exception {
        mockMvc.perform(
                        patch("/api/v1/admin/configs/" + key)
                                .with(user(new CustomUserDetails(admin)))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"value\":\"" + on + "\"}"))
                .andExpect(status().isOk());
    }

    /** 메일 알림을 켠 받는 사람. */
    protected Member receiver(String tag) {
        Member receiver = member(tag, Role.ROLE_USER);
        settings.update(receiver.getId(), true);
        return receiver;
    }

    protected Member member(String tag, Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member saved =
                transactions.execute(
                        status ->
                                members.save(
                                        Member.builder()
                                                .email(tag + "-" + unique + "@test.snowthing.org")
                                                .password("encoded")
                                                .nickname(tag + unique)
                                                .emailVerifiedAt(now())
                                                .role(role)
                                                .status(MemberStatus.ACTIVE)
                                                .build()));
        memberIds.add(saved.getId());
        return saved;
    }

    protected long sendDm(Member from, Member to, String content) {
        return Long.parseLong(
                directMessages
                        .send(
                                new CustomUserDetails(from),
                                new DirectMessageSendRequest(to.getPublicId(), content, null))
                        .id());
    }

    protected List<Map<String, Object>> rows() {
        return jdbc.queryForList("SELECT * FROM dm_mail_outbox ORDER BY event_id");
    }

    protected Map<String, Object> rowOf(long directMessageId) {
        return jdbc.queryForMap(
                "SELECT * FROM dm_mail_outbox WHERE direct_message_id = ?", directMessageId);
    }

    protected Map<String, Object> single() {
        List<Map<String, Object>> rows = rows();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    protected static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
