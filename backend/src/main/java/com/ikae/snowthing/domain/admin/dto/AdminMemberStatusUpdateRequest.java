package com.ikae.snowthing.domain.admin.dto;

import jakarta.validation.constraints.NotNull;

import com.ikae.snowthing.domain.member.entity.MemberStatus;

public record AdminMemberStatusUpdateRequest(@NotNull MemberStatus status) {}
