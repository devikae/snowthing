package com.ikae.snowthing.domain.dmmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository;
import com.ikae.snowthing.domain.dmmail.service.DmMailScheduler;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 서버 설정(환경변수)이 꺼진 상태의 쪽지 메일 (IKA-109). 지금 운영과 같은 조건이다: 환경변수를 넣지 않아 기록과 발송이 모두 false.
 *
 * <p>이 상태에서는 관리자 화면 스위치를 켜도 기록도 발송도 하지 않아야 한다.
 */
@SpringBootTest(properties = {"dm-mail-test.real-switches=true"})
@AutoConfigureMockMvc
@Import(DmMailTestConfig.class)
class DmMailServerBlockIntegrationTest extends DmMailSwitchTestSupport {

    @Autowired private DmMailScheduler scheduler;
    @MockitoSpyBean private DmMailOutboxRepository outbox;

    private Member receiver;

    @BeforeEach
    void setUp() throws Exception {
        prepare();
        receiver = receiver("recv");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    @DisplayName("환경변수가 꺼져 있으면 화면 스위치를 둘 다 켜도 발송 요청을 쓰지 않고 메일도 보내지 않는다")
    void serverSettingBlocksBothSwitches() throws Exception {
        setSwitch(ENQUEUE, true);
        setSwitch(SEND, true);

        sendDm(alice, receiver, "서버 설정에서 막힌 상태");
        // 이미 쌓여 있던 요청이 있어도 보내지 않는다.
        long dmId = sendDm(alice, receiver, "미리 쌓여 있던 요청");
        transactions.executeWithoutResult(
                status -> outbox.insertIfAbsent(dmId, receiver.getId(), now()));
        scheduler.poll();

        assertThat(single().get("status")).isEqualTo("PENDING");
        assertThat(DmMailTestConfig.FAKE_SES.requests()).isZero();
    }

    @Test
    @DisplayName("스위치 상태 조회는 화면 스위치가 켜져 있어도 서버 설정에서 막혀 실제로는 꺼져 있다고 알려 준다")
    void switchesReportServerBlock() throws Exception {
        setSwitch(ENQUEUE, true);
        setSwitch(SEND, true);

        mockMvc.perform(
                        get("/api/v1/admin/dm-mail/switches")
                                .with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enqueue.serverAllowed").value(false))
                .andExpect(jsonPath("$.enqueue.switchOn").value(true))
                .andExpect(jsonPath("$.enqueue.effective").value(false))
                .andExpect(jsonPath("$.send.serverAllowed").value(false))
                .andExpect(jsonPath("$.send.switchOn").value(true))
                .andExpect(jsonPath("$.send.effective").value(false));
    }

    @Test
    @DisplayName("발송이 꺼진 동안 폴링은 정리 간격(10분) 안에서 DB를 건드리지 않는다")
    void pollingWhileOffDoesNotTouchDatabase() {
        // 첫 주기는 기한 지난 행 정리를 한 번 한다.
        scheduler.poll();
        clearInvocations(outbox);

        scheduler.poll();
        scheduler.poll();
        scheduler.poll();

        verifyNoInteractions(outbox);
    }
}
