package com.ikae.snowthing.domain.dmmail.mail;

/**
 * 메일 공급자가 꺼져 있을 때 쓴다. 워커는 {@link #enabled()}가 false면 작업을 잡지 않는다. 행은 PENDING으로 남다가 발송 기한이 지나면 스케줄러
 * 정리에서 SKIPPED가 된다.
 */
public class DisabledDmMailSender implements DmMailSender {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public DmMailResult send(DmMail mail) {
        return new DmMailResult.NotSent("MAIL_DISABLED");
    }
}
