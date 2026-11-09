package com.ikae.snowthing.domain.push.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.push.dto.PushConfigResponse;
import com.ikae.snowthing.domain.push.dto.PushSubscriptionRequest;
import com.ikae.snowthing.domain.push.dto.PushUnsubscribeRequest;
import com.ikae.snowthing.domain.push.repository.PushSubscriptionRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.ApiTokenUserDetails;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 내 기기의 웹 푸시 구독 등록과 해지. 설계: docs/conception/029_web_push/01_design.md
 *
 * <p>알림 API와 같이 로그인 세션 회원만 쓴다. 위임 토큰과 API 토큰은 기기를 가진 사람이 아니라서 401로 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    /** 회원 한 명이 둘 수 있는 기기 수. 넘으면 오래 안 쓴 기기부터 지운다. 가짜 endpoint를 잔뜩 넣어 발송을 부풀리는 것도 막는다. */
    static final int MAX_SUBSCRIPTIONS_PER_MEMBER = 10;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int P256_PUBLIC_KEY_LENGTH = 65;
    private static final int AUTH_SECRET_LENGTH = 16;
    private static final int UPSERT_ATTEMPTS = 3;

    private final VapidKeys vapidKeys;
    private final PushSubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public PushConfigResponse config(CustomUserDetails userDetails) {
        requireMemberId(userDetails);
        return new PushConfigResponse(vapidKeys.enabled(), vapidKeys.publicKey());
    }

    /**
     * 같은 기기를 다시 등록하면 키와 최근 사용 시각을 고치고, 다른 계정이었다면 지금 회원으로 옮긴다.
     *
     * <p>넣기와 개수 정리는 각각 바로 커밋한다. 둘을 한 트랜잭션으로 묶으면 정리 단계의 범위 락까지 오래 잡혀 동시 등록이 교착에 빠지기 쉽다. 정리가 실패해도 다음
     * 등록 때 다시 정리된다.
     */
    public void subscribe(CustomUserDetails userDetails, PushSubscriptionRequest request) {
        Long memberId = requireMemberId(userDetails);
        if (!vapidKeys.enabled()) {
            throw new CustomException(ErrorCode.PUSH_DISABLED);
        }
        if (PushEndpointPolicy.parse(request.endpoint()) == null
                || !validKey(request.keys().p256dh(), P256_PUBLIC_KEY_LENGTH)
                || !validKey(request.keys().auth(), AUTH_SECRET_LENGTH)) {
            throw new CustomException(ErrorCode.PUSH_SUBSCRIPTION_INVALID);
        }
        upsertWithRetry(memberId, request);
        try {
            subscriptionRepository.deleteOlderThanRecent(memberId, MAX_SUBSCRIPTIONS_PER_MEMBER);
        } catch (CannotAcquireLockException e) {
            log.info("웹 푸시 구독 개수 정리를 다음 등록으로 미룹니다 memberId={}", memberId);
        }
    }

    /** 내 구독만 지운다. 없거나 남의 구독이면 아무것도 하지 않는다. 발송이 꺼져 있어도 끌 수는 있다. */
    public void unsubscribe(CustomUserDetails userDetails, PushUnsubscribeRequest request) {
        Long memberId = requireMemberId(userDetails);
        subscriptionRepository.deleteByMemberAndEndpointHash(
                memberId, PushEndpointPolicy.hash(request.endpoint()));
    }

    /** 같은 endpoint를 동시에 넣으면 InnoDB가 교착으로 한쪽을 되돌린다. 문장 하나라 다시 실행해도 결과가 같다. */
    private void upsertWithRetry(Long memberId, PushSubscriptionRequest request) {
        String hash = PushEndpointPolicy.hash(request.endpoint());
        for (int attempt = 1; ; attempt++) {
            try {
                subscriptionRepository.upsert(
                        memberId,
                        request.endpoint(),
                        hash,
                        request.keys().p256dh(),
                        request.keys().auth(),
                        LocalDateTime.now(clock.withZone(KST)));
                return;
            } catch (CannotAcquireLockException e) {
                if (attempt >= UPSERT_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private static boolean validKey(String value, int expectedLength) {
        byte[] decoded;
        try {
            decoded = WebPushCrypto.base64UrlDecode(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (decoded.length != expectedLength) {
            return false;
        }
        if (expectedLength == P256_PUBLIC_KEY_LENGTH) {
            try {
                WebPushCrypto.decodePublic(decoded);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return true;
    }

    private static Long requireMemberId(CustomUserDetails userDetails) {
        if (userDetails == null
                || userDetails instanceof BotDelegatedUserDetails
                || userDetails instanceof ApiTokenUserDetails
                || userDetails.getMember() == null
                || userDetails.getMember().getId() == null) {
            throw new CustomAuthException(ErrorCode.NOTIFICATION_LOGIN_REQUIRED);
        }
        return userDetails.getMember().getId();
    }
}
