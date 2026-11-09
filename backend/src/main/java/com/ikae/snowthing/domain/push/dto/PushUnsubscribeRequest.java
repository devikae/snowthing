package com.ikae.snowthing.domain.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushUnsubscribeRequest(@NotBlank @Size(max = 1024) String endpoint) {}
