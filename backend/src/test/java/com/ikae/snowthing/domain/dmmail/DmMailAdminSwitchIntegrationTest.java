package com.ikae.snowthing.domain.dmmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import com.ikae.snowthing.domain.dmmail.service.DmMailScheduler;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 쪽지 메일 관리자 스위치 통합 테스트 (IKA-109, 실제 MySQL + 가짜 SES HTTP 서버).
 *
 * <p>서버 설정(환경변수)은 기록과 발송을 모두 허용한 상태다. 화면 스위치는 실제 빈과 실제 관리자 API를 쓴다. 폴링은 스스로 돌지 않으므로 스케줄러의 poll()을
 * 직접 불러 "다음 주기"를 재현한다. 정리 간격을 0으로 둬서 발송이 꺼진 주기마다 기한 지난 행 정리가 돈다.
 */
@SpringBootTest(
        properties = {
            "snowthing.dm-mail.enqueue-enabled=true",
            "snowthing.dm-mail.send-enabled=true",
            "snowthing.dm-mail.stale-sweep-interval=0s",
            "dm-mail-test.real-switches=true"
        })
@AutoConfigureMockMvc
@Import(DmMailTestConfig.class)
class DmMailAdminSwitchIntegrationTest extends DmMailSwitchTestSupport {

    @Autowired private DmMailScheduler scheduler;

    private Member receiver;

    @BeforeEach
    void setUp() throws Exception {
        prepare();
        receiver = receiver("recv");
        // 스케줄러는 직전 주기의 발송 상태를 기억한다. 앞 테스트가 켜 둔 채 끝났어도 꺼진 주기를 한 번 돌려 맞춘다.
        scheduler.poll();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Nested
    @DisplayName("스위치 조합")
    class Combinations {

        @Test
        @DisplayName("기록 꺼짐, 발송 꺼짐: 발송 요청도 메일도 없다")
        void bothOff() {
            sendDm(alice, receiver, "둘 다 꺼짐");
            scheduler.poll();

            assertThat(rows()).isEmpty();
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isZero();
        }

        @Test
        @DisplayName("기록 켜짐, 발송 꺼짐: 발송 요청은 PENDING으로 쌓이고 메일은 나가지 않는다")
        void enqueueOnly() throws Exception {
            setSwitch(ENQUEUE, true);

            sendDm(alice, receiver, "기록만");
            scheduler.poll();

            assertThat(single().get("status")).isEqualTo("PENDING");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isZero();
        }

        @Test
        @DisplayName("기록 꺼짐, 발송 켜짐: 새 쪽지는 기록하지 않고, 이미 쌓인 요청은 보낸다")
        void sendOnly() throws Exception {
            setSwitch(ENQUEUE, true);
            long queued = sendDm(alice, receiver, "기록이 켜져 있을 때 온 쪽지");
            setSwitch(ENQUEUE, false);
            setSwitch(SEND, true);

            Member other = receiver("other");
            sendDm(alice, other, "기록이 꺼진 뒤 온 쪽지");
            scheduler.poll();

            assertThat(rows()).hasSize(1);
            assertThat(rowOf(queued).get("status")).isEqualTo("ACCEPTED");
            assertThat(DmMailTestConfig.FAKE_SES.accepted()).isEqualTo(1);
        }

        @Test
        @DisplayName("기록 켜짐, 발송 켜짐: 쪽지 하나에 메일 한 통이 나간다")
        void bothOn() throws Exception {
            setSwitch(ENQUEUE, true);
            setSwitch(SEND, true);

            sendDm(alice, receiver, "둘 다 켜짐");
            scheduler.poll();

            assertThat(single().get("status")).isEqualTo("ACCEPTED");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);
            assertThat(DmMailTestConfig.FAKE_SES.accepted()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("실행 중 전환")
    class LiveToggle {

        @Test
        @DisplayName("발송을 끄면 다음 주기부터 메일이 0통이고, 다시 켜면 재시작 없이 보낸다")
        void sendOffStopsFromNextTick() throws Exception {
            setSwitch(ENQUEUE, true);
            setSwitch(SEND, true);
            sendDm(alice, receiver, "켜져 있을 때");
            scheduler.poll();
            assertThat(DmMailTestConfig.FAKE_SES.accepted()).isEqualTo(1);

            setSwitch(SEND, false);
            Member second = receiver("second");
            long waiting = sendDm(alice, second, "꺼진 뒤");
            scheduler.poll();
            scheduler.poll();

            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);
            assertThat(rowOf(waiting).get("status")).isEqualTo("PENDING");

            setSwitch(SEND, true);
            scheduler.poll();

            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(2);
            assertThat(rowOf(waiting).get("status")).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("기록을 끄면 다음 쪽지부터 발송 요청을 쓰지 않는다")
        void enqueueOffStopsFromNextMessage() throws Exception {
            setSwitch(ENQUEUE, true);
            sendDm(alice, receiver, "켜져 있을 때");
            setSwitch(ENQUEUE, false);

            sendDm(alice, receiver, "꺼진 뒤");

            assertThat(rows()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("오래된 대기 건")
    class Stale {

        @Test
        @DisplayName("발송을 다시 켜면 기한이 지난 대기 건은 건너뛰고 기한 안의 건만 보낸다")
        void turningOnSkipsStaleRows() throws Exception {
            setSwitch(ENQUEUE, true);
            long old = sendDm(alice, receiver, "이틀 전 쪽지");
            Member retrying = receiver("retry");
            long oldRetry = sendDm(alice, retrying, "재시도 대기 중이던 오래된 쪽지");
            jdbc.update("UPDATE dm_mail_outbox SET created_at = ?", now().minusHours(25));
            jdbc.update(
                    "UPDATE dm_mail_outbox SET status = 'RETRY_WAIT' WHERE direct_message_id = ?",
                    oldRetry);
            Member fresh = receiver("fresh");
            long recent = sendDm(alice, fresh, "방금 쪽지");

            setSwitch(SEND, true);
            scheduler.poll();

            assertThat(rowOf(old).get("status")).isEqualTo("SKIPPED");
            assertThat(rowOf(old).get("skip_reason")).isEqualTo("SEND_OFF_EXPIRED");
            assertThat(rowOf(oldRetry).get("status")).isEqualTo("SKIPPED");
            assertThat(rowOf(oldRetry).get("skip_reason")).isEqualTo("SEND_OFF_EXPIRED");
            assertThat(rowOf(recent).get("status")).isEqualTo("ACCEPTED");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);
        }

        @Test
        @DisplayName("발송이 꺼져 있어도 기한이 지난 대기 건은 정리되고, 기한 안의 건은 그대로 기다린다")
        void staleRowsAreSkippedWhileSendIsOff() throws Exception {
            setSwitch(ENQUEUE, true);
            long old = sendDm(alice, receiver, "오래된 쪽지");
            jdbc.update("UPDATE dm_mail_outbox SET created_at = ?", now().minusHours(25));
            Member fresh = receiver("fresh");
            long recent = sendDm(alice, fresh, "방금 쪽지");

            scheduler.poll();

            assertThat(rowOf(old).get("skip_reason")).isEqualTo("SEND_OFF_EXPIRED");
            assertThat(rowOf(recent).get("status")).isEqualTo("PENDING");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isZero();
        }

        @Test
        @DisplayName("정리된 건은 보관 기간이 지나면 기존 정리 작업이 지운다")
        void skippedRowsArePurgedAfterRetention() throws Exception {
            setSwitch(ENQUEUE, true);
            sendDm(alice, receiver, "오래된 쪽지");
            jdbc.update("UPDATE dm_mail_outbox SET created_at = ?", now().minusDays(40));
            scheduler.poll();
            assertThat(single().get("status")).isEqualTo("SKIPPED");

            jdbc.update("UPDATE dm_mail_outbox SET updated_at = ?", now().minusDays(31));
            scheduler.purge();

            assertThat(rows()).isEmpty();
        }
    }

    @Nested
    @DisplayName("관리자 API")
    class AdminApi {

        @Test
        @DisplayName("스위치 상태 조회는 서버 허용 여부, 화면 스위치, 실제 동작 여부를 따로 보여 준다")
        void switchesShowEachLayer() throws Exception {
            setSwitch(ENQUEUE, true);

            mockMvc.perform(
                            get("/api/v1/admin/dm-mail/switches")
                                    .with(user(new CustomUserDetails(admin))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enqueue.configKey").value(ENQUEUE))
                    .andExpect(jsonPath("$.enqueue.serverAllowed").value(true))
                    .andExpect(jsonPath("$.enqueue.switchOn").value(true))
                    .andExpect(jsonPath("$.enqueue.effective").value(true))
                    .andExpect(jsonPath("$.send.configKey").value(SEND))
                    .andExpect(jsonPath("$.send.serverAllowed").value(true))
                    .andExpect(jsonPath("$.send.switchOn").value(false))
                    .andExpect(jsonPath("$.send.effective").value(false))
                    .andExpect(jsonPath("$.mailProviderReady").value(true))
                    .andExpect(jsonPath("$.deadlineHours").value(24));
        }

        @Test
        @DisplayName("스위치를 바꾸면 누가 무엇을 어떤 값으로 바꿨는지 조치 기록에 남는다")
        void changeIsRecordedInActionLog() throws Exception {
            setSwitch(SEND, true);
            setSwitch(SEND, false);

            List<Map<String, Object>> logs =
                    jdbc.queryForList(
                            "SELECT action, target_type, target_id, reason FROM admin_action_log"
                                    + " WHERE actor_member_id = ? ORDER BY admin_action_log_id",
                            admin.getId());
            assertThat(logs).hasSize(2);
            assertThat(logs.get(0))
                    .containsEntry("action", "CONFIG_UPDATE")
                    .containsEntry("target_type", "CONFIG")
                    .containsEntry("target_id", SEND)
                    .containsEntry("reason", "true");
            assertThat(logs.get(1))
                    .containsEntry("target_id", SEND)
                    .containsEntry("reason", "false");
        }

        @Test
        @DisplayName("일반 회원과 비회원은 스위치를 보거나 바꿀 수 없고, 값도 바뀌지 않는다")
        void onlyAdminCanReadOrChange() throws Exception {
            mockMvc.perform(
                            get("/api/v1/admin/dm-mail/switches")
                                    .with(user(new CustomUserDetails(alice))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/dm-mail/switches"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(
                            patch("/api/v1/admin/configs/" + SEND)
                                    .with(user(new CustomUserDetails(alice)))
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"value\":\"true\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(
                            patch("/api/v1/admin/configs/" + SEND)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"value\":\"true\"}"))
                    .andExpect(status().isUnauthorized());

            assertThat(configValue(SEND)).isEqualTo("false");
        }

        @Test
        @DisplayName("관리자여도 CSRF 토큰이 없으면 바꿀 수 없다")
        void changeRequiresCsrf() throws Exception {
            mockMvc.perform(
                            patch("/api/v1/admin/configs/" + SEND)
                                    .with(user(new CustomUserDetails(admin)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"value\":\"true\"}"))
                    .andExpect(status().isForbidden());

            assertThat(configValue(SEND)).isEqualTo("false");
        }

        private String configValue(String key) {
            return jdbc.queryForObject(
                    "SELECT config_value FROM system_config WHERE config_key = ?",
                    String.class,
                    key);
        }
    }
}
