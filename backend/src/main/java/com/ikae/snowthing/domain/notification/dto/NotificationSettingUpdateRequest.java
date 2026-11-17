package com.ikae.snowthing.domain.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingUpdateRequest(@NotNull Boolean dmEmailEnabled) {}
