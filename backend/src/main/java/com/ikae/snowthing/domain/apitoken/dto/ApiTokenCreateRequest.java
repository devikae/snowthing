package com.ikae.snowthing.domain.apitoken.dto;

import java.util.List;

/**
 * API 토큰 발급 요청. 범위는 문자열로 받아 서비스에서 검증한다. enum으로 받으면 알 수 없는 값이 본문 해석 오류가 되어 어떤 규칙을 어겼는지 알려 주기 어렵다.
 *
 * @param scopes READ, DRAFT 중 1개 이상
 * @param expiresInDays 1~30. 비우면 30
 */
public record ApiTokenCreateRequest(String name, List<String> scopes, Integer expiresInDays) {}
