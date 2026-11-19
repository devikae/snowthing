package com.ikae.snowthing.domain.image.service;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 사진 디코딩을 서버 전체에서 동시에 몇 건까지 할지 정한다.
 *
 * <p>동시 2건의 근거: 사진은 최대 2,500만 화소까지 받는다. 디코딩하면 화소당 4바이트라 한 장에 약 100MB가 힙에 올라가고, 축소 과정의 중간 버퍼까지 치면 한
 * 건이 순간적으로 150MB 안팎을 쓴다. 2건이면 300MB 정도로, 운영 힙(호스트 2GB의 50%, 약 1GB)에서 라이브톡과 일반 요청 몫을 남길 수 있는 선이다.
 *
 * <p>사용자 요청은 자리가 날 때까지 잠깐 기다린다. 한 건은 보통 몇 초 안에 끝나므로 개장일처럼 몇 명이 동시에 올려도 차례로 처리된다. 기다려도 자리가 안 나면 횟수
 * 초과(429)와 구분되는 혼잡 응답(503)을 준다.
 *
 * <p>백그라운드 썸네일은 기다리지 않고, 기다리는 사용자가 있으면 양보하며, 한 번에 한 자리까지만 쓴다. 사용자 몫을 뺏지 않게 하려는 것이다.
 */
@Component
public class ImageProcessingGate {
    static final int PERMITS = 2;
    static final Duration USER_WAIT = Duration.ofSeconds(5);

    /** 백그라운드 작업이 자리가 없어 미뤄졌음을 알린다. 실패가 아니라서 재시도 횟수에 넣지 않는다. */
    public static class BusyException extends RuntimeException {
        public BusyException() {
            super("image processing is busy", null, false, false);
        }
    }

    // 공정 모드: 기다리는 사용자가 먼저 온 순서대로 자리를 받는다.
    private final Semaphore permits;
    private final Semaphore backgroundPermits = new Semaphore(1);
    private final Duration userWait;

    @Autowired
    public ImageProcessingGate() {
        this(PERMITS, USER_WAIT);
    }

    ImageProcessingGate(int permits, Duration userWait) {
        this.permits = new Semaphore(permits, true);
        this.userWait = userWait;
    }

    /** 사용자 요청. 자리가 날 때까지 잠깐 기다리고, 못 받으면 IMAGE_PROCESSING_BUSY(503)다. */
    public <T> T run(Supplier<T> operation) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(userWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.IMAGE_PROCESSING_BUSY);
        }
        if (!acquired) throw new CustomException(ErrorCode.IMAGE_PROCESSING_BUSY);
        try {
            return operation.get();
        } finally {
            permits.release();
        }
    }

    boolean hasQueuedUsers() {
        return permits.hasQueuedThreads();
    }

    /** 백그라운드 작업. 기다리지 않고, 자리가 없거나 사용자가 기다리고 있으면 BusyException으로 다음 주기에 미룬다. */
    public <T> T runInBackground(Supplier<T> operation) {
        if (!backgroundPermits.tryAcquire()) throw new BusyException();
        try {
            boolean acquired;
            try {
                // 시간 0의 tryAcquire는 공정 모드를 지켜, 기다리는 사용자가 있으면 끼어들지 않는다.
                acquired = permits.tryAcquire(0, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new BusyException();
            }
            if (!acquired) throw new BusyException();
            try {
                return operation.get();
            } finally {
                permits.release();
            }
        } finally {
            backgroundPermits.release();
        }
    }
}
