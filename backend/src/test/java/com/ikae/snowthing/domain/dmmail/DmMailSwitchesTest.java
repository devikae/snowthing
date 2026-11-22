package com.ikae.snowthing.domain.dmmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ikae.snowthing.domain.admin.service.SystemConfigService;
import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.mail.DmMailSender;
import com.ikae.snowthing.domain.dmmail.service.DmMailSwitches;

/** 최종 동작 = 서버 설정(환경변수) AND 관리자 화면 스위치. 발송은 메일 공급자까지 켜져 있어야 한다 (IKA-109). */
class DmMailSwitchesTest {

    @ParameterizedTest(name = "기록: 환경변수 {0}, 화면 스위치 {1} → {2}")
    @CsvSource({"false,false,false", "false,true,false", "true,false,false", "true,true,true"})
    @DisplayName("기록은 환경변수와 화면 스위치가 모두 켜져 있을 때만 켜진다")
    void enqueue(boolean env, boolean adminSwitch, boolean expected) {
        SystemConfigService config = mock(SystemConfigService.class);
        when(config.isDmMailEnqueueEnabled()).thenReturn(adminSwitch);
        DmMailSwitches switches = new DmMailSwitches(properties(env, false), config, sender(true));

        assertThat(switches.enqueueOn()).isEqualTo(expected);
        assertThat(switches.snapshot().enqueueEffective()).isEqualTo(expected);
        assertThat(switches.snapshot().enqueueServerAllowed()).isEqualTo(env);
        assertThat(switches.snapshot().enqueueSwitchOn()).isEqualTo(adminSwitch);
    }

    @ParameterizedTest(name = "발송: 환경변수 {0}, 화면 스위치 {1}, 메일 공급자 {2} → {3}")
    @CsvSource({
        "false,false,true,false",
        "false,true,true,false",
        "true,false,true,false",
        "true,true,true,true",
        "true,true,false,false"
    })
    @DisplayName("발송은 환경변수, 화면 스위치, 메일 공급자가 모두 켜져 있을 때만 켜진다")
    void send(boolean env, boolean adminSwitch, boolean provider, boolean expected) {
        SystemConfigService config = mock(SystemConfigService.class);
        when(config.isDmMailSendEnabled()).thenReturn(adminSwitch);
        DmMailSwitches switches =
                new DmMailSwitches(properties(false, env), config, sender(provider));

        assertThat(switches.sendOn()).isEqualTo(expected);
        assertThat(switches.snapshot().sendEffective()).isEqualTo(expected);
        assertThat(switches.snapshot().sendServerAllowed()).isEqualTo(env);
        assertThat(switches.snapshot().mailProviderReady()).isEqualTo(provider);
    }

    private static DmMailProperties properties(boolean enqueue, boolean send) {
        return new DmMailProperties(
                enqueue, send, null, null, null, null, 0, null, null, 0, null, null, null);
    }

    private static DmMailSender sender(boolean enabled) {
        DmMailSender sender = mock(DmMailSender.class);
        when(sender.enabled()).thenReturn(enabled);
        return sender;
    }
}
