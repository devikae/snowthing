package com.ikae.snowthing.domain.dmmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.dmmail.DmMailTestConfig.SwitchableSender;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository;
import com.ikae.snowthing.domain.dmmail.service.DmMailOutboxWriter;
import com.ikae.snowthing.domain.dmmail.service.DmMailProcessor;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberWithdrawalRepository;
import com.ikae.snowthing.domain.message.dto.DirectMessageSendRequest;
import com.ikae.snowthing.domain.message.entity.DirectMessage;
import com.ikae.snowthing.domain.message.service.DirectMessageService;
import com.ikae.snowthing.domain.notification.service.NotificationSettingService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.testsupport.FakeSesServer.Mode;

/**
 * 쪽지 도착 메일 Outbox 통합 테스트 (실제 MySQL 8.0 + 가짜 SES HTTP 서버).
 *
 * <p>트랜잭션 경계 자체를 검증하므로 테스트 트랜잭션을 쓰지 않고 각 단계를 커밋한다. 스케줄러는 꺼져 있어 처리기를 직접 부르고, 쿨다운과 lease 만료는 처리기에 시각을
 * 넘겨 재현한다. 가짜 SES는 받은 HTTP 요청 수와 수락 수를 따로 세서, 앱이 본 결과와 공급자 쪽 실제 수락을 비교한다.
 */
@SpringBootTest(properties = {"snowthing.dm-mail.enqueue-enabled=true"})
@AutoConfigureMockMvc
@Import(DmMailTestConfig.class)
class DmMailOutboxIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MemberRepository members;
    @Autowired private DirectMessageService directMessages;
    @Autowired private NotificationSettingService settings;
    @Autowired private DmMailOutboxWriter writer;
    @Autowired private DmMailProcessor processor;
    @Autowired private SwitchableSender sender;
    @Autowired private MemberWithdrawalRepository withdrawals;
    @MockitoSpyBean private DmMailOutboxRepository outbox;

    private final List<Long> memberIds = new ArrayList<>();
    private Member alice;
    private Member receiver;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM dm_mail_outbox");
        jdbc.update("DELETE FROM dm_mail_recipient_state");
        DmMailTestConfig.FAKE_SES.reset(Mode.ACCEPT);
        sender.useFake();
        alice = member("alice", true);
        receiver = member("recv", true);
        settings.update(receiver.getId(), true);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM dm_mail_outbox");
        jdbc.update("DELETE FROM dm_mail_recipient_state");
        for (Long id : memberIds) {
            jdbc.update(
                    "DELETE FROM direct_message WHERE sender_id = ? OR receiver_id = ?", id, id);
            jdbc.update("DELETE FROM member_notification_setting WHERE member_id = ?", id);
        }
        for (Long id : memberIds) {
            jdbc.update("DELETE FROM member WHERE member_id = ?", id);
        }
        memberIds.clear();
    }

    @Nested
    @DisplayName("쪽지와 발송 요청의 원자성")
    class Atomicity {

        @Test
        @DisplayName("쪽지가 커밋되면 발송 요청이 PENDING 한 행으로 남는다")
        void commitWritesOneRow() {
            long dmId = sendDm(alice, receiver, "안녕하세요");

            List<Map<String, Object>> rows = rows();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("direct_message_id")).isEqualTo(dmId);
            assertThat(rows.get(0).get("recipient_member_id")).isEqualTo(receiver.getId());
            assertThat(rows.get(0).get("status")).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("쪽지 트랜잭션이 롤백되면 쪽지도 발송 요청도 남지 않는다")
        void rollbackLeavesNothing() {
            transactions.executeWithoutResult(
                    status -> {
                        sendDm(alice, receiver, "롤백될 쪽지");
                        status.setRollbackOnly();
                    });

            assertThat(messageCount(receiver)).isZero();
            assertThat(rows()).isEmpty();
        }

        @Test
        @DisplayName("발송 요청 쓰기가 실패하면 쪽지도 롤백된다")
        void outboxFailureRollsBackMessage() {
            doThrow(new DataAccessResourceFailureException("outbox down"))
                    .when(outbox)
                    .insertIfAbsent(anyLong(), anyLong(), any());

            assertThatThrownBy(() -> sendDm(alice, receiver, "실패할 쪽지"))
                    .isInstanceOf(DataAccessResourceFailureException.class);
            assertThat(messageCount(receiver)).isZero();
            assertThat(rows()).isEmpty();
        }

        @Test
        @DisplayName("발송 요청 쓰기는 트랜잭션 밖에서 부르면 바로 실패한다")
        void writerRequiresTransaction() {
            assertThatThrownBy(() -> writer.messageSent(mock(DirectMessage.class)))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }

        @Test
        @DisplayName("같은 쪽지의 발송 요청을 다시 쓰면 유니크 제약으로 한 행만 남는다")
        void duplicateEnqueueKeepsOneRow() {
            long dmId = sendDm(alice, receiver, "한 번만");

            transactions.executeWithoutResult(
                    status -> outbox.insertIfAbsent(dmId, receiver.getId(), now()));

            assertThat(rows()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("알림 설정")
    class Settings {

        @Test
        @DisplayName("설정 행이 없는 기존 회원에게는 발송 요청을 만들지 않는다")
        void existingMemberWithoutSettingIsNotEnqueued() {
            Member legacy = member("legacy", true);

            sendDm(alice, legacy, "기존 회원에게");

            assertThat(rows()).isEmpty();
        }

        @Test
        @DisplayName("적재 뒤 알림을 끄면 발송 직전 확인에서 SKIPPED가 되고 SES를 부르지 않는다")
        void turnedOffAfterEnqueueIsSkipped() {
            sendDm(alice, receiver, "끄기 전에 보낸 쪽지");
            settings.update(receiver.getId(), false);

            processor.processBatch(now());

            assertThat(single().get("status")).isEqualTo("SKIPPED");
            assertThat(single().get("skip_reason")).isEqualTo("NOTIFICATION_OFF");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isZero();
        }

        @Test
        @DisplayName("소셜 내부 대체 주소와 미인증 이메일은 받을 수 없는 주소로 보고 건너뛴다")
        void undeliverableEmailIsSkipped() {
            Member social = memberWithEmail("social", "naver_1@users.invalid", true);
            Member unverified = member("unverified", false);
            settings.update(social.getId(), true);
            settings.update(unverified.getId(), true);

            sendDm(alice, social, "소셜 회원에게");
            sendDm(alice, unverified, "미인증 회원에게");
            processor.processBatch(now());

            assertThat(rows())
                    .extracting(row -> row.get("skip_reason"))
                    .containsOnly("EMAIL_UNDELIVERABLE");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isZero();
        }

        @Test
        @DisplayName("설정 API는 행이 없으면 꺼짐을, 받을 수 없는 주소면 emailDeliverable=false를 돌려준다")
        void settingsApi() throws Exception {
            Member legacy = member("api", true);
            Member social = memberWithEmail("apisocial", "kakao_9@users.invalid", true);

            mockMvc.perform(
                            get("/api/v1/members/me/notification-settings")
                                    .with(user(new CustomUserDetails(legacy))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dmEmailEnabled").value(false))
                    .andExpect(jsonPath("$.emailDeliverable").value(true));
            mockMvc.perform(
                            put("/api/v1/members/me/notification-settings")
                                    .with(user(new CustomUserDetails(legacy)))
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"dmEmailEnabled\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dmEmailEnabled").value(true));
            assertThat(settings.isDmEmailEnabled(legacy.getId())).isTrue();
            mockMvc.perform(
                            get("/api/v1/members/me/notification-settings")
                                    .with(user(new CustomUserDetails(social))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emailDeliverable").value(false));
            mockMvc.perform(
                            put("/api/v1/members/me/notification-settings")
                                    .with(user(new CustomUserDetails(legacy)))
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("탈퇴하면 설정, 받은 발송 요청, 받는 사람 상태 행을 지운다")
        void withdrawalErasesMailRows() {
            sendDm(alice, receiver, "탈퇴 전 쪽지");
            processor.processBatch(now());
            sendDm(alice, receiver, "탈퇴 직전 쪽지");

            transactions.executeWithoutResult(
                    status ->
                            withdrawals.erasePersonalData(
                                    receiver.getId(),
                                    receiver.getNickname(),
                                    receiver.getEmail(),
                                    List.of(receiver.getEmail()),
                                    "withdrawn-" + receiver.getId() + "@withdrawn.invalid",
                                    now()));

            assertThat(rows()).isEmpty();
            assertThat(count("member_notification_setting", "member_id", receiver.getId()))
                    .isZero();
            assertThat(count("dm_mail_recipient_state", "recipient_member_id", receiver.getId()))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("묶음 메일")
    class Bundling {

        @Test
        @DisplayName("첫 쪽지는 바로 보내고, 쿨다운 동안 온 쪽지 4개는 쿨다운이 끝난 뒤 한 통으로 보낸다")
        void firstImmediateThenBundled() throws Exception {
            sendDm(alice, receiver, "1");
            LocalDateTime t0 = now();
            processor.processBatch(t0);
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);

            for (int i = 2; i <= 5; i++) {
                sendDm(alice, receiver, String.valueOf(i));
            }
            processor.processBatch(t0.plusMinutes(1));
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);
            assertThat(rows())
                    .filteredOn(row -> "PENDING".equals(row.get("status")))
                    .hasSize(4)
                    .allSatisfy(
                            row ->
                                    assertThat(toLocal(row.get("due_at")))
                                            .isEqualTo(t0.plusMinutes(10)));

            processor.processBatch(t0.plusMinutes(10).plusSeconds(1));

            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(2);
            assertThat(mailText(1)).contains("쪽지 4개가 도착했어요");
            assertThat(mailText(0)).contains("쪽지가 도착했어요").doesNotContain("개가");
            List<Map<String, Object>> rows = rows();
            assertThat(rows).extracting(row -> row.get("status")).containsOnly("ACCEPTED");
            assertThat(rows.subList(1, 5))
                    .extracting(row -> row.get("provider_message_id"))
                    .containsOnly("fake-2");
        }

        @Test
        @DisplayName("쿨다운 동안 읽은 쪽지는 묶음에서 빠진다")
        void readDuringCooldownIsExcluded() throws Exception {
            sendDm(alice, receiver, "1");
            LocalDateTime t0 = now();
            processor.processBatch(t0);
            long read = sendDm(alice, receiver, "읽을 쪽지");
            sendDm(alice, receiver, "안 읽을 쪽지");
            jdbc.update(
                    "UPDATE direct_message SET read_at = ? WHERE direct_message_id = ?", t0, read);

            processor.processBatch(t0.plusMinutes(10).plusSeconds(1));

            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(2);
            assertThat(mailText(1)).contains("쪽지가 도착했어요").doesNotContain("개가");
            assertThat(rowOf(read).get("skip_reason")).isEqualTo("ALREADY_READ");
        }

        @Test
        @DisplayName("전부 읽었으면 SES를 부르지 않고, 쿨다운 기준 시각도 되돌린다")
        void allReadMeansNoCall() {
            long dmId = sendDm(alice, receiver, "바로 읽은 쪽지");
            jdbc.update(
                    "UPDATE direct_message SET read_at = ? WHERE direct_message_id = ?",
                    now(),
                    dmId);

            processor.processBatch(now());

            assertThat(single().get("status")).isEqualTo("SKIPPED");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isZero();
            assertThat(lastAttemptAt(receiver)).isNull();
        }

        @Test
        @DisplayName("보낸 사람이 여럿이면 두 명까지 이름을 쓰고 나머지는 외 N명으로 줄인다")
        void multipleSenders() throws Exception {
            Member bob = member("bob", true);
            Member carol = member("carol", true);
            sendDm(alice, receiver, "a");
            sendDm(bob, receiver, "b");
            sendDm(carol, receiver, "c");

            processor.processBatch(now());

            assertThat(mailText(0))
                    .contains(
                            alice.getNickname()
                                    + "님, "
                                    + bob.getNickname()
                                    + "님 외 1명에게서 쪽지 3개가 도착했어요")
                    .doesNotContain("a\n")
                    .contains("/messages");
        }
    }

    @Nested
    @DisplayName("SES 결과에 따른 상태")
    class Outcomes {

        @Test
        @DisplayName("429는 요청이 처리되지 않은 거절이라 RETRY_WAIT로 미루고, 다음 시도는 쿨다운에 막히지 않는다")
        void throttledIsRetried() {
            DmMailTestConfig.FAKE_SES.reset(Mode.THROTTLE);
            sendDm(alice, receiver, "재시도");
            LocalDateTime t0 = now();

            processor.processBatch(t0);

            Map<String, Object> row = single();
            assertThat(row.get("status")).isEqualTo("RETRY_WAIT");
            assertThat(row.get("attempts")).isEqualTo(1);
            assertThat((String) row.get("last_error_code")).startsWith("SES_429");
            assertThat(toLocal(row.get("due_at"))).isAfter(t0);
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);
            assertThat(lastAttemptAt(receiver)).isNull();

            DmMailTestConfig.FAKE_SES.reset(Mode.ACCEPT);
            processor.processBatch(toLocal(row.get("due_at")));

            assertThat(single().get("status")).isEqualTo("ACCEPTED");
            assertThat(single().get("attempts")).isEqualTo(2);
        }

        @Test
        @DisplayName("연결 거부는 요청이 나가지 않았음이 확실해 RETRY_WAIT다")
        void connectionRefusedIsRetried() {
            sender.useDeadEndpoint();
            sendDm(alice, receiver, "연결 거부");

            processor.processBatch(now());

            assertThat(single().get("status")).isEqualTo("RETRY_WAIT");
            assertThat((String) single().get("last_error_code")).startsWith("CONNECT_");
        }

        @Test
        @DisplayName("재시도 한도를 다 쓰면 FAILED다")
        void retryExhaustedFails() {
            DmMailTestConfig.FAKE_SES.reset(Mode.THROTTLE);
            sendDm(alice, receiver, "한도");
            jdbc.update("UPDATE dm_mail_outbox SET attempts = 4");

            processor.processBatch(now());

            assertThat(single().get("status")).isEqualTo("FAILED");
            assertThat((String) single().get("last_error_code")).startsWith("RETRY_EXHAUSTED_");
        }

        @Test
        @DisplayName("400 영구 거절은 반복하지 않고 FAILED다")
        void rejectedFails() {
            DmMailTestConfig.FAKE_SES.reset(Mode.REJECT);
            sendDm(alice, receiver, "거절");

            processor.processBatch(now());

            assertThat(single().get("status")).isEqualTo("FAILED");
            assertThat((String) single().get("last_error_code")).startsWith("SES_400");
        }

        @Test
        @DisplayName("타임아웃 A: 수락 전에 끊겨도 앱은 구분할 수 없어 UNKNOWN이고, 자동 재발송은 0회다 (수락 0건)")
        void timeoutBeforeAcceptIsUnknown() {
            DmMailTestConfig.FAKE_SES.reset(Mode.DROP_BEFORE_ACCEPT);
            sendDm(alice, receiver, "타임아웃 A");
            LocalDateTime t0 = now();

            processor.processBatch(t0);
            processor.processBatch(t0.plusHours(1));

            assertThat(single().get("status")).isEqualTo("UNKNOWN");
            assertThat((String) single().get("last_error_code")).startsWith("NO_RESPONSE_");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);
            assertThat(DmMailTestConfig.FAKE_SES.accepted()).isZero();
        }

        @Test
        @DisplayName("타임아웃 B: 수락 후 응답이 사라져도 UNKNOWN이고, 자동 재발송은 0회다 (수락 1건)")
        void timeoutAfterAcceptIsUnknown() {
            DmMailTestConfig.FAKE_SES.reset(Mode.ACCEPT_THEN_HANG);
            sendDm(alice, receiver, "타임아웃 B");
            LocalDateTime t0 = now();

            processor.processBatch(t0);
            processor.processBatch(t0.plusHours(1));

            assertThat(single().get("status")).isEqualTo("UNKNOWN");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);
            assertThat(DmMailTestConfig.FAKE_SES.accepted()).isEqualTo(1);
        }

        @Test
        @DisplayName("500은 처리 중 실패인지 알 수 없어 UNKNOWN이고, SDK 재시도가 꺼져 요청은 정확히 한 번 나간다")
        void serverErrorIsUnknownAndNotRetriedBySdk() {
            DmMailTestConfig.FAKE_SES.reset(Mode.SERVER_ERROR);
            sendDm(alice, receiver, "500");

            processor.processBatch(now());

            assertThat(single().get("status")).isEqualTo("UNKNOWN");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);
        }

        @Test
        @DisplayName("관리자 요약은 상태별 건수, 확인할 건수, 가장 오래 기다린 요청을 보여 주고 일반 회원은 볼 수 없다")
        void adminSummary() throws Exception {
            DmMailTestConfig.FAKE_SES.reset(Mode.SERVER_ERROR);
            sendDm(alice, receiver, "UNKNOWN이 될 쪽지");
            processor.processBatch(now());
            Member other = member("pending", true);
            settings.update(other.getId(), true);
            sendDm(alice, other, "대기 중인 쪽지");
            Member admin = member("admin", true, Role.ROLE_ADMIN);

            mockMvc.perform(
                            get("/api/v1/admin/dm-mail/outbox/summary")
                                    .with(user(new CustomUserDetails(admin))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.counts.UNKNOWN").value(1))
                    .andExpect(jsonPath("$.counts.PENDING").value(1))
                    .andExpect(jsonPath("$.counts.ACCEPTED").value(0))
                    .andExpect(jsonPath("$.attentionCount").value(1))
                    .andExpect(jsonPath("$.oldestOpenAgeSeconds").isNumber());
            mockMvc.perform(
                            get("/api/v1/admin/dm-mail/outbox/summary")
                                    .with(user(new CustomUserDetails(alice))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("쪽지 생성 후 24시간이 지나면 보내지 않는다")
        void deadlineExpiredIsSkipped() {
            sendDm(alice, receiver, "오래된 쪽지");
            jdbc.update("UPDATE dm_mail_outbox SET created_at = ?", now().minusHours(25));

            processor.processBatch(now());

            assertThat(single().get("skip_reason")).isEqualTo("EXPIRED");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isZero();
        }
    }

    @Nested
    @DisplayName("선점과 경합")
    class Claiming {

        @Test
        @DisplayName("lease가 끝난 PROCESSING은 UNKNOWN이 되고, 늦게 돌아온 워커의 결과 기록은 영향 행 0으로 거부된다")
        void expiredLeaseBecomesUnknownAndStaleWriteIsRejected() {
            sendDm(alice, receiver, "멈춘 워커");
            LocalDateTime t0 = now();
            List<Long> ids =
                    transactions.execute(
                            status -> {
                                outbox.lockRecipient(receiver.getId());
                                return outbox
                                        .claim(
                                                receiver.getId(),
                                                t0,
                                                "dead-worker",
                                                t0.plusSeconds(60))
                                        .stream()
                                        .map(DmMailOutboxRepository.ClaimedRow::eventId)
                                        .toList();
                            });
            assertThat(single().get("status")).isEqualTo("PROCESSING");

            processor.processBatch(t0.plusSeconds(61));

            assertThat(single().get("status")).isEqualTo("UNKNOWN");
            assertThat(single().get("last_error_code")).isEqualTo("LEASE_EXPIRED");
            int changed =
                    transactions.execute(
                            status -> outbox.markAccepted(ids, "dead-worker", "late", now()));
            assertThat(changed).isZero();
            assertThat(single().get("status")).isEqualTo("UNKNOWN");
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isZero();
        }

        @Test
        @DisplayName("워커 두 개가 같은 받는 사람을 동시에 처리해도 메일은 한 통이고 묶음이 쪼개지지 않는다")
        void twoWorkersSameRecipient() throws Exception {
            for (int i = 0; i < 3; i++) {
                sendDm(alice, receiver, "동시 " + i);
            }
            DmMailTestConfig.FAKE_SES.acceptDelay(Duration.ofMillis(300));
            LocalDateTime t0 = now();

            List<Boolean> results =
                    runConcurrently(2, () -> processor.processRecipient(receiver.getId(), t0));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(1);
            assertThat(mailText(0)).contains("쪽지 3개");
            assertThat(rows())
                    .extracting(row -> row.get("provider_message_id"))
                    .containsOnly("fake-1");
        }

        @Test
        @DisplayName("워커 두 개가 동시에 폴링해도 받는 사람마다 메일은 정확히 한 통이다")
        void twoWorkersManyRecipients() throws Exception {
            List<Member> receivers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Member r = member("multi" + i, true);
                settings.update(r.getId(), true);
                receivers.add(r);
                sendDm(alice, r, "여러 명 " + i);
            }
            DmMailTestConfig.FAKE_SES.acceptDelay(Duration.ofMillis(100));
            LocalDateTime t0 = now();

            runConcurrently(2, () -> processor.processBatch(t0));

            assertThat(DmMailTestConfig.FAKE_SES.requests()).isEqualTo(4);
            Set<String> recipients = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                recipients.add(mailJson(i).at("/Destination/ToAddresses/0").asText());
            }
            assertThat(recipients)
                    .containsExactlyInAnyOrderElementsOf(
                            receivers.stream().map(Member::getEmail).toList());
            assertThat(rows()).extracting(row -> row.get("status")).containsOnly("ACCEPTED");
        }

        @Test
        @DisplayName("커밋된 발송 요청은 워커가 아직 돌지 않았어도 남아 있다가 나중에 처리된다")
        void committedRowIsProcessedLater() {
            sendDm(alice, receiver, "재기동 후 처리");
            assertThat(single().get("status")).isEqualTo("PENDING");

            processor.processBatch(now());

            assertThat(single().get("status")).isEqualTo("ACCEPTED");
        }
    }

    private <T> List<T> runConcurrently(int workers, java.util.concurrent.Callable<T> task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return task.call();
                                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Member member(String tag, boolean verified) {
        return member(tag, verified, Role.ROLE_USER);
    }

    private Member member(String tag, boolean verified, Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return memberWithEmail(tag, tag + "-" + unique + "@test.snowthing.org", verified, role);
    }

    private Member memberWithEmail(String tag, String email, boolean verified) {
        return memberWithEmail(tag, email, verified, Role.ROLE_USER);
    }

    private Member memberWithEmail(String tag, String email, boolean verified, Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member saved =
                transactions.execute(
                        status ->
                                members.save(
                                        Member.builder()
                                                .email(email)
                                                .password("encoded")
                                                .nickname(tag + unique)
                                                .emailVerifiedAt(verified ? now() : null)
                                                .role(role)
                                                .status(MemberStatus.ACTIVE)
                                                .build()));
        memberIds.add(saved.getId());
        return saved;
    }

    private long sendDm(Member from, Member to, String content) {
        return Long.parseLong(
                directMessages
                        .send(
                                new CustomUserDetails(from),
                                new DirectMessageSendRequest(to.getPublicId(), content, null))
                        .id());
    }

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("SELECT * FROM dm_mail_outbox ORDER BY event_id");
    }

    private Map<String, Object> single() {
        List<Map<String, Object>> rows = rows();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private Map<String, Object> rowOf(long directMessageId) {
        return jdbc.queryForMap(
                "SELECT * FROM dm_mail_outbox WHERE direct_message_id = ?", directMessageId);
    }

    private int messageCount(Member to) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM direct_message WHERE receiver_id = ?",
                Integer.class,
                to.getId());
    }

    private int count(String table, String column, long id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
    }

    private LocalDateTime lastAttemptAt(Member recipient) {
        List<LocalDateTime> values =
                jdbc.queryForList(
                        "SELECT last_attempt_at FROM dm_mail_recipient_state"
                                + " WHERE recipient_member_id = ?",
                        LocalDateTime.class,
                        recipient.getId());
        return values.isEmpty() ? null : values.get(0);
    }

    private JsonNode mailJson(int index) throws Exception {
        return objectMapper.readTree(DmMailTestConfig.FAKE_SES.acceptedBodies().get(index));
    }

    private String mailText(int index) throws Exception {
        return mailJson(index).at("/Content/Simple/Body/Text/Data").asText();
    }

    private static LocalDateTime toLocal(Object value) {
        return value instanceof java.sql.Timestamp ts
                ? ts.toLocalDateTime()
                : (LocalDateTime) value;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
