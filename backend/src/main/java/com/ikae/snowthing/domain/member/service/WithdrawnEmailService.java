package com.ikae.snowthing.domain.member.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.email.security.EmailVerificationHasher;
import com.ikae.snowthing.domain.member.repository.WithdrawnEmailRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴 후 30일 재가입 차단. 만료 여부는 조회할 때 시각으로 판단하므로 스케줄러가 늦어도 30일이 지나면 바로 풀린다.
 *
 * <p>이메일은 흔한 값이라 단순 SHA-256은 사전 대입으로 되돌릴 수 있다. 그래서 서버 비밀값을 쓰는 HMAC으로 저장한다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawnEmailService {

    static final int BLOCK_DAYS = 30;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WithdrawnEmailRepository repository;
    private final EmailVerificationHasher hasher;
    private final EmailNormalizer normalizer;
    private final Clock clock;

    /** 회원 익명화와 함께 커밋되도록 탈퇴 트랜잭션 안에서만 부른다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void block(Collection<String> emails) {
        LocalDateTime now = now();
        for (String email : emails) {
            repository.upsert(hash(email), now, now.plusDays(BLOCK_DAYS));
        }
    }

    public boolean isBlocked(String email) {
        return repository.existsByEmailHashAndExpiresAtAfter(hash(email), now());
    }

    public void requireNotBlocked(String email) {
        if (isBlocked(email)) {
            throw new CustomException(ErrorCode.WITHDRAWN_EMAIL_RESTRICTED);
        }
    }

    private String hash(String email) {
        return hasher.digestWithdrawnEmail(normalizer.normalize(email));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(KST));
    }
}
