package com.ikae.snowthing.domain.image.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class ImageProcessingGateTest {

    @Test
    @DisplayName("동시 업로드 3건은 자리가 날 때까지 기다렸다가 모두 처리된다")
    void threeConcurrentUploadsAllComplete() throws Exception {
        var gate = new ImageProcessingGate();
        var running = new AtomicInteger();
        var maxRunning = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                int number = i;
                results.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return gate.run(
                                            () -> {
                                                maxRunning.accumulateAndGet(
                                                        running.incrementAndGet(), Math::max);
                                                sleep(300);
                                                running.decrementAndGet();
                                                return number;
                                            });
                                }));
            }
            start.countDown();
            for (int i = 0; i < 3; i++)
                assertThat(results.get(i).get(10, TimeUnit.SECONDS)).isEqualTo(i);
        }
        assertThat(maxRunning.get()).isEqualTo(ImageProcessingGate.PERMITS);
    }

    @Test
    @DisplayName("기다려도 자리가 안 나면 횟수 초과와 다른 혼잡 에러(503 FILE_009)다")
    void fullGateAfterWaitReturnsBusyError() throws Exception {
        var gate = new ImageProcessingGate(1, Duration.ofMillis(100));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var holder = executor.submit(() -> gate.run(() -> hold(entered, release)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> gate.run(() -> true))
                        .isInstanceOfSatisfying(
                                CustomException.class,
                                failure -> {
                                    assertThat(failure.getErrorCode())
                                            .isEqualTo(ErrorCode.IMAGE_PROCESSING_BUSY);
                                    assertThat(failure.getErrorCode().getStatus())
                                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                                });
            } finally {
                release.countDown();
            }
            assertThat(holder.get(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThatThrownBy(
                        () ->
                                gate.run(
                                        () -> {
                                            throw new IllegalStateException("test");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(gate.run(() -> true)).isTrue();
    }

    @Test
    @DisplayName("백그라운드 작업은 한 자리만 쓰고, 자리가 없으면 기다리지 않고 미룬다")
    void backgroundUsesAtMostOnePermitAndNeverWaits() throws Exception {
        var gate = new ImageProcessingGate();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var background =
                    executor.submit(() -> gate.runInBackground(() -> hold(entered, release)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                // 백그라운드가 한 자리를 쓰는 중이라 두 번째 백그라운드는 바로 미뤄진다.
                long started = System.nanoTime();
                assertThatThrownBy(() -> gate.runInBackground(() -> true))
                        .isInstanceOf(ImageProcessingGate.BusyException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - started))
                        .isLessThan(Duration.ofSeconds(1));
                // 사용자 몫은 남아 있다.
                assertThat(gate.run(() -> true)).isTrue();
            } finally {
                release.countDown();
            }
            assertThat(background.get(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(gate.runInBackground(() -> true)).isTrue();
    }

    @Test
    @DisplayName("사용자가 기다리고 있으면 백그라운드 작업은 끼어들지 않는다")
    void backgroundYieldsToWaitingUser() throws Exception {
        var gate = new ImageProcessingGate(1, Duration.ofSeconds(5));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> gate.run(() -> hold(entered, release)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var waitingUser = executor.submit(() -> gate.run(() -> true));
            try {
                waitUntilQueued(gate);
                assertThatThrownBy(() -> gate.runInBackground(() -> true))
                        .isInstanceOf(ImageProcessingGate.BusyException.class);
            } finally {
                release.countDown();
            }
            assertThat(holder.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(waitingUser.get(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void waitUntilQueued(ImageProcessingGate gate) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!gate.hasQueuedUsers()) {
            if (System.nanoTime() > deadline) throw new AssertionError("user did not queue");
            Thread.sleep(10);
        }
    }

    private static boolean hold(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        return true;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
