package com.ikae.snowthing.domain.dmmail.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 받는 사람별 쪽지 메일 발송 상태 (052). 쿨다운의 기준 시각이자, 같은 받는 사람을 두 워커가 동시에 묶지 못하게 하는 잠금 지점이다. 읽기와 쓰기는 {@code
 * DmMailOutboxRepository}가 JDBC로 한다.
 */
@Entity
@Table(name = "dm_mail_recipient_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmMailRecipientState {

    @Id
    @Column(name = "recipient_member_id")
    private Long recipientMemberId;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
}
