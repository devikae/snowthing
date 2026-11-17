package com.ikae.snowthing.domain.notification.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별 알림 설정 (052). 행이 없으면 모든 알림이 꺼진 것으로 본다.
 *
 * <p>기존 회원은 행이 없어 꺼짐이고, 신규 가입 때 켜짐으로 행을 만든다. 가입 경로 하나에서 생성을 빠뜨려도 원치 않는 메일이 나가지 않는 쪽으로 틀린다.
 */
@Entity
@Table(name = "member_notification_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberNotificationSetting {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "dm_email_enabled", nullable = false)
    private boolean dmEmailEnabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public MemberNotificationSetting(Long memberId, boolean dmEmailEnabled, LocalDateTime now) {
        this.memberId = memberId;
        this.dmEmailEnabled = dmEmailEnabled;
        this.updatedAt = now;
    }

    public void changeDmEmail(boolean enabled, LocalDateTime now) {
        this.dmEmailEnabled = enabled;
        this.updatedAt = now;
    }
}
