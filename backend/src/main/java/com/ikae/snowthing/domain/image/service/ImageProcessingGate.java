package com.ikae.snowthing.domain.image.service;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@Component
public class ImageProcessingGate {
    private final Semaphore permits = new Semaphore(2);

    public <T> T run(Supplier<T> operation) {
        if (!permits.tryAcquire()) throw new CustomException(ErrorCode.PROFILE_ACTION_LIMIT);
        try {
            return operation.get();
        } finally {
            permits.release();
        }
    }
}
