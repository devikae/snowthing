package com.ikae.snowthing.domain.dmmail.mail;

/** 메일 공급자가 꺼져 있을 때 쓴다. 워커는 {@link #enabled()}가 false면 작업을 잡지 않으므로 행은 PENDING으로 남는다. */
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
