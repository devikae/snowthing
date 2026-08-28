package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberResortRepository;
import com.ikae.snowthing.domain.member.repository.MemberRidingStyleRepository;

@SpringBootTest
class MemberConcurrencyTest {

    @Autowired private MemberService memberService;

    @Autowired private MemberRepository memberRepository;

    @Autowired private MemberResortRepository memberResortRepository;

    @Autowired private MemberRidingStyleRepository memberRidingStyleRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        memberResortRepository.deleteAll();
        memberRidingStyleRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("[검증 3] 10개 멀티스레드로 동일한 이메일 동시 가입 시 정확히 1건만 성공하고 9건은 예외 처리되어야 한다")
    void signUp_Concurrency_OnlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        MemberSignUpRequest signUpRequest =
                MemberSignUpRequest.builder()
                        .email("concurrent@snowthing.com")
                        .password("Password123!")
                        .nickname("동시성보더")
                        .build();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(
                    () -> {
                        try {
                            readyLatch.countDown();
                            startLatch.await();

                            memberService.signUp(signUpRequest);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
        assertThat(memberRepository.count()).isEqualTo(1);
    }
}
