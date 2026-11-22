package com.ikae.snowthing.domain.dmmail.service;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.admin.service.SystemConfigService;
import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.mail.DmMailSender;

import lombok.RequiredArgsConstructor;

/**
 * 쪽지 메일 기록과 발송이 지금 켜져 있는지 판단한다 (IKA-109).
 *
 * <p>최종 동작 = 서버 설정(환경변수) AND 관리자 화면 스위치(system_config). 환경변수는 재시작해야 바뀌는 비상 차단이고, 화면 스위치는 재시작 없이
 * 바뀐다. 발송은 여기에 메일 공급자(ses)가 켜져 있는지도 더한다.
 *
 * <p>화면 스위치는 {@link SystemConfigService}의 캐시(5분)에서 읽는다. 쪽지 저장과 1초 폴링마다 불러도 DB 조회는 키마다 5분에 한 번이다.
 * 관리자 API로 바꾸면 같은 서버의 캐시가 바로 바뀌어 다음 쪽지, 다음 폴링부터 반영된다. DB 값을 직접 고치면 최대 5분 뒤에 반영된다.
 */
@Component
@RequiredArgsConstructor
public class DmMailSwitches {

    private final DmMailProperties properties;
    private final SystemConfigService systemConfig;
    private final DmMailSender sender;

    /** 쪽지 트랜잭션에서 발송 요청을 쓸지. */
    public boolean enqueueOn() {
        return properties.enqueueEnabled() && systemConfig.isDmMailEnqueueEnabled();
    }

    /** 워커가 메일을 보낼지. */
    public boolean sendOn() {
        return properties.sendEnabled() && sender.enabled() && systemConfig.isDmMailSendEnabled();
    }

    /** 관리자 화면에 보여 줄 현재 상태. */
    public Snapshot snapshot() {
        return new Snapshot(
                properties.enqueueEnabled(),
                systemConfig.isDmMailEnqueueEnabled(),
                properties.sendEnabled(),
                systemConfig.isDmMailSendEnabled(),
                sender.enabled());
    }

    /**
     * @param enqueueServerAllowed 환경변수가 기록을 허용하는지
     * @param enqueueSwitchOn 관리자 화면 기록 스위치
     * @param sendServerAllowed 환경변수가 발송을 허용하는지
     * @param sendSwitchOn 관리자 화면 발송 스위치
     * @param mailProviderReady 메일 공급자가 ses로 켜져 있는지
     */
    public record Snapshot(
            boolean enqueueServerAllowed,
            boolean enqueueSwitchOn,
            boolean sendServerAllowed,
            boolean sendSwitchOn,
            boolean mailProviderReady) {

        public boolean enqueueEffective() {
            return enqueueServerAllowed && enqueueSwitchOn;
        }

        public boolean sendEffective() {
            return sendServerAllowed && mailProviderReady && sendSwitchOn;
        }
    }
}
