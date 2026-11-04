package com.ikae.snowthing.domain.apitoken.dto;

/** 발급 응답. {@code token}은 평문이며 이 응답에서 한 번만 내려간다. 서버는 해시만 저장하므로 다시 볼 수 없다. */
public record ApiTokenCreateResponse(String token, ApiTokenResponse apiToken) {}
