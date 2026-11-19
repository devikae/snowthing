package com.ikae.snowthing.domain.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.notification.entity.MemberNotificationSetting;

public interface MemberNotificationSettingRepository
        extends JpaRepository<MemberNotificationSetting, Long> {}
