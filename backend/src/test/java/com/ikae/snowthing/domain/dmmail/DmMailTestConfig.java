package com.ikae.snowthing.domain.dmmail;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.mail.DmMail;
import com.ikae.snowthing.domain.dmmail.mail.DmMailResult;
import com.ikae.snowthing.domain.dmmail.mail.DmMailSender;
import com.ikae.snowthing.domain.dmmail.mail.SesDmMailSender;
import com.ikae.snowthing.domain.dmmail.service.DmMailSwitches;
import com.ikae.snowthing.testsupport.FakeSesServer;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * 쪽지 메일 테스트 설정. 실제 {@link SesDmMailSender}를 가짜 SES 서버에 연결한다. SDK 재시도 끄기와 예외 분류를 실제 HTTP로 검증하기 위해서다.
 *
 * <p>연결 거부를 만들 때는 {@link SwitchableSender#useDeadEndpoint()}로 아무도 듣지 않는 포트를 쓰는 발송기로 바꾼다.
 */
@TestConfiguration
public class DmMailTestConfig {

    public static final FakeSesServer FAKE_SES = new FakeSesServer();

    /**
     * 테스트 기본 제한 시간은 짧게(시도 0.6초, 전체 0.9초) 둬서 타임아웃 시나리오를 빨리 끝낸다. 벤치마크는 운영과 같은 값(3초, 5초)을 속성으로 넘긴다.
     */
    @Bean
    @Primary
    public SwitchableSender switchableDmMailSender(
            @Value("${dm-mail-test.attempt-timeout-ms:600}") long attemptMs,
            @Value("${dm-mail-test.call-timeout-ms:900}") long callMs) {
        Duration attempt = Duration.ofMillis(attemptMs);
        Duration call = Duration.ofMillis(callMs);
        return new SwitchableSender(
                sender(FAKE_SES.endpoint(), attempt, call),
                sender("http://127.0.0.1:" + deadPort(), attempt, call));
    }

    /**
     * 관리자 화면 스위치(system_config)는 켜진 것으로 본다. 기록과 발송은 테스트 속성 {@code snowthing.dm-mail.*-enabled}만으로
     * 정해진다. 화면 스위치 자체를 검증하는 테스트는 {@code dm-mail-test.real-switches=true}로 실제 빈을 쓴다.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
            name = "dm-mail-test.real-switches",
            havingValue = "false",
            matchIfMissing = true)
    public DmMailSwitches adminSwitchesAlwaysOn(DmMailProperties properties) {
        return new DmMailSwitches(properties, null, null) {
            @Override
            public boolean enqueueOn() {
                return properties.enqueueEnabled();
            }

            @Override
            public boolean sendOn() {
                return properties.sendEnabled();
            }

            @Override
            public Snapshot snapshot() {
                return new Snapshot(
                        properties.enqueueEnabled(), true, properties.sendEnabled(), true, true);
            }
        };
    }

    static SesDmMailSender sender(String endpoint, Duration attempt, Duration call) {
        return new SesDmMailSender(
                SesDmMailSender.createClient(
                        "ap-northeast-2",
                        new DmMailProperties.Ses(attempt, call, endpoint),
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test", "test"))),
                "help@snowthing.org",
                "SnowThing",
                "");
    }

    /** 잠깐 열었다 닫은 포트. 연결하면 거부된다. */
    private static int deadPort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static class SwitchableSender implements DmMailSender {

        private final DmMailSender fake;
        private final DmMailSender dead;
        private volatile DmMailSender current;

        SwitchableSender(DmMailSender fake, DmMailSender dead) {
            this.fake = fake;
            this.dead = dead;
            this.current = fake;
        }

        public void useFake() {
            current = fake;
        }

        public void useDeadEndpoint() {
            current = dead;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public DmMailResult send(DmMail mail) {
            return current.send(mail);
        }
    }
}
