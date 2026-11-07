package com.ikae.snowthing.domain.member.dto;

import java.util.List;

public record AdminMemberListResponse(
        List<AdminMemberResponse> items, int page, int size, boolean hasNext) {}
