package com.ikae.snowthing.domain.dmmail.mail;

/** 보낼 메일 한 통. 받는 주소는 로그에 남기지 않는다. */
public record DmMail(String to, String subject, String text, String html) {}
