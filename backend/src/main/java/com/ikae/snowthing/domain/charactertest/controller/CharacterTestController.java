package com.ikae.snowthing.domain.charactertest.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.charactertest.dto.CharacterGroupPreviewResponse;
import com.ikae.snowthing.domain.charactertest.dto.CharacterJoinRequest;
import com.ikae.snowthing.domain.charactertest.dto.CharacterJoinResponse;
import com.ikae.snowthing.domain.charactertest.dto.CharacterMapResponse;
import com.ikae.snowthing.domain.charactertest.dto.CharacterResultRequest;
import com.ikae.snowthing.domain.charactertest.dto.CharacterResultResponse;
import com.ikae.snowthing.domain.charactertest.model.CharacterAnswers;
import com.ikae.snowthing.domain.charactertest.service.CharacterGroupService;
import com.ikae.snowthing.domain.charactertest.service.CharacterResultService;
import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

/**
 * 가입 없이 쓰는 캐릭터 테스트. 공개 경로는 SecurityConfig에 메서드와 경로를 정확히 적었고 CSRF 검사는 그대로 받는다. 접근 토큰은 쿠키가 아니라 전용
 * 헤더로만 받아 URL과 접근 로그에 남지 않는다. 회원 기능과 분리되어 있어 쓰기 제한은 로그인 여부와 관계없이 IP로 묶는다.
 */
@RestController
@RequiredArgsConstructor
public class CharacterTestController {

    static final String ACCESS_TOKEN_HEADER = "X-Character-Token";

    private final CharacterResultService resultService;
    private final CharacterGroupService groupService;
    private final ContentWriteRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    /** 모임에 들어가기 전 결과 화면용. 계산만 하고 저장하지 않으므로 쓰기 제한을 쓰지 않는다. */
    @PostMapping("/api/v1/character-test/result")
    public ResponseEntity<CharacterResultResponse> computeResult(
            @Valid @RequestBody CharacterResultRequest request) {
        return noStore(resultService.compute(new CharacterAnswers(request.answers())));
    }

    @PostMapping("/api/v1/character-groups")
    public ResponseEntity<CharacterJoinResponse> createGroup(
            @Valid @RequestBody CharacterJoinRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check(
                null,
                clientIpResolver.resolve(httpRequest),
                ContentWriteRateLimiter.Action.CHARACTER_GROUP_CREATE);
        return created(groupService.create(request));
    }

    @GetMapping("/api/v1/character-groups/{publicId}")
    public ResponseEntity<CharacterGroupPreviewResponse> getGroup(@PathVariable String publicId) {
        return noStore(groupService.getPreview(publicId));
    }

    @PostMapping("/api/v1/character-groups/{publicId}/participants")
    public ResponseEntity<CharacterJoinResponse> joinGroup(
            @PathVariable String publicId,
            @Valid @RequestBody CharacterJoinRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.check(
                null,
                clientIpResolver.resolve(httpRequest),
                ContentWriteRateLimiter.Action.CHARACTER_GROUP_JOIN);
        return created(groupService.join(publicId, request));
    }

    @GetMapping("/api/v1/character-groups/{publicId}/map")
    public ResponseEntity<CharacterMapResponse> getMap(
            @PathVariable String publicId,
            @RequestHeader(name = ACCESS_TOKEN_HEADER, required = false) String accessToken) {
        return noStore(groupService.getMap(publicId, accessToken));
    }

    @DeleteMapping("/api/v1/character-participants/me")
    public ResponseEntity<Void> leave(
            @RequestHeader(name = ACCESS_TOKEN_HEADER, required = false) String accessToken) {
        groupService.leave(accessToken);
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static <T> ResponseEntity<T> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
