package com.ikae.snowthing.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.notification.dto.NotificationSettingResponse;
import com.ikae.snowthing.domain.notification.entity.MemberNotificationSetting;
import com.ikae.snowthing.domain.notification.repository.MemberNotificationSettingRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/** 회원 알림 설정. 행이 없으면 꺼짐으로 본다. */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final MemberNotificationSettingRepository settings;
    private final MemberRepository members;
    private final Clock clock;

    /** 신규 가입 회원은 쪽지 메일 알림을 켠 채로 시작한다. 가입 트랜잭션 안에서 부른다. */
    @Transactional
    public void initializeForNewMember(Long memberId, LocalDateTime now) {
        if (!settings.existsById(memberId)) {
            settings.save(new MemberNotificationSetting(memberId, true, now));
        }
    }

    @Transactional(readOnly = true)
    public boolean isDmEmailEnabled(Long memberId) {
        return settings.findById(memberId)
                .map(MemberNotificationSetting::isDmEmailEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public NotificationSettingResponse get(Long memberId) {
        return toResponse(memberId, isDmEmailEnabled(memberId));
    }

    @Transactional
    public NotificationSettingResponse update(Long memberId, boolean dmEmailEnabled) {
        LocalDateTime now = LocalDateTime.now(clock);
        settings.findById(memberId)
                .ifPresentOrElse(
                        setting -> setting.changeDmEmail(dmEmailEnabled, now),
                        () ->
                                settings.save(
                                        new MemberNotificationSetting(
                                                memberId, dmEmailEnabled, now)));
        return toResponse(memberId, dmEmailEnabled);
    }

    private NotificationSettingResponse toResponse(Long memberId, boolean dmEmailEnabled) {
        Member member =
                members.findById(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        return new NotificationSettingResponse(
                dmEmailEnabled,
                MailDeliverability.isDeliverable(member.getEmail(), member.getEmailVerifiedAt()));
    }
}
