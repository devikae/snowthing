package com.ikae.snowthing.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMemberNicknameUpdateRequest(
        @NotBlank @Size(min = 2, max = 20) String nickname) {}
