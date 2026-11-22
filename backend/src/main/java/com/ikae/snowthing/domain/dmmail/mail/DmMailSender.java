package com.ikae.snowthing.domain.dmmail.mail;

/** 쪽지 도착 메일 발송. 인증메일 발송기({@code VerificationEmailSender})와 클라이언트, 재시도 정책을 공유하지 않는다. */
public interface DmMailSender {

    /** 실제로 메일을 보낼 수 있는지. false면 워커가 작업을 선점하지 않는다. */
    boolean enabled();

    /** 예외를 던지지 않고 결과를 분류해 돌려준다. 호출 한 번에 HTTP 요청은 최대 한 번이다. */
    DmMailResult send(DmMail mail);
}
