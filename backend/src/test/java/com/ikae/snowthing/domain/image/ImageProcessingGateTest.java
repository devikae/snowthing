package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.*;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.image.service.ImageProcessingGate;
import com.ikae.snowthing.global.exception.CustomException;

class ImageProcessingGateTest {
    @Test
    void excessConcurrentWorkIsRejectedAndExceptionsReleaseCapacity() throws Exception {
        var gate = new ImageProcessingGate();
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> work =
                    () ->
                            gate.run(
                                    () -> {
                                        entered.countDown();
                                        try {
                                            if (!release.await(5, TimeUnit.SECONDS))
                                                throw new AssertionError("timeout");
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new AssertionError(e);
                                        }
                                        return true;
                                    });
            var first = executor.submit(work);
            var second = executor.submit(work);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> gate.run(() -> true)).isInstanceOf(CustomException.class);
            } finally {
                release.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
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
}
