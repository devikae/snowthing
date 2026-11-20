package com.ikae.snowthing.domain.admin.dto;

import java.util.List;

/** 개장일 전체와 메인 개장 배너 스위치. 공개 조회와 관리자 조회가 같은 모양을 쓴다. 목록은 배너 순서, 같으면 ID 순. */
/** bannerTitle은 배너 맨 앞 문구다(기본 "개장까지"). */
public record SeasonOpeningResponse(
        boolean bannerEnabled, String bannerTitle, List<SeasonOpeningItem> resorts) {}
