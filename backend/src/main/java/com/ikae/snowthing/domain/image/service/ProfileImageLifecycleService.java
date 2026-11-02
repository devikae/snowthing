package com.ikae.snowthing.domain.image.service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.domain.image.entity.ProfileImageCleanup;
import com.ikae.snowthing.domain.image.repository.ProfileImageCleanupRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProfileImageLifecycleService {
    private final ImageService images;
    private final MemberRepository members;
    private final ProfileImageCleanupRepository jobs;
    private final EmailNormalizer normalizer;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ProfileImageLifecycleService(
            ImageService images,
            MemberRepository members,
            ProfileImageCleanupRepository jobs,
            EmailNormalizer normalizer,
            Clock clock,
            PlatformTransactionManager manager) {
        this.images = images;
        this.members = members;
        this.jobs = jobs;
        this.normalizer = normalizer;
        this.clock = clock;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ImageUploadResponse upload(String email, MultipartFile file) {
        String key = "public/profiles/" + UUID.randomUUID() + ".jpg";
        String url = images.profileUrl(key);
        // Commit recovery intent before touching S3, including for a process crash.
        transactions.executeWithoutResult(
                status ->
                        jobs.saveAndFlush(
                                new ProfileImageCleanup(
                                        key, url, clock.instant().plus(Duration.ofHours(24)))));
        try {
            ImageUploadResponse uploaded = images.uploadProfileImage(file, key);
            transactions.executeWithoutResult(
                    status -> {
                        Member member = member(email);
                        queuePrevious(member.getProfileImageUrl());
                        member.updateProfile(
                                member.getNickname(),
                                member.getBio(),
                                member.getDepartureRegion(),
                                uploaded.imageUrl());
                        jobs.deleteById(key); // Removal and account attachment commit atomically.
                    });
            return uploaded;
        } catch (RuntimeException exception) {
            try {
                transactions.executeWithoutResult(
                        status ->
                                jobs.findForUpdate(key)
                                        .ifPresent(job -> job.retryAt(clock.instant())));
                cleanOne(key);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure); // Durable job remains for retry.
            }
            throw exception;
        }
    }

    public void reset(String email) {
        transactions.executeWithoutResult(
                status -> {
                    Member member = member(email);
                    queuePrevious(member.getProfileImageUrl());
                    member.updateProfile(
                            member.getNickname(),
                            member.getBio(),
                            member.getDepartureRegion(),
                            null);
                });
    }

    private Member member(String email) {
        return members.findByEmailForUpdate(normalizer.normalize(email))
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void queuePrevious(String url) {
        images.profileKey(url)
                .ifPresent(key -> jobs.save(new ProfileImageCleanup(key, url, clock.instant())));
    }

    @Scheduled(fixedDelayString = "${snowthing.profile-images.cleanup-delay-ms:60000}")
    public void cleanup() {
        for (var job :
                jobs.findByNotBeforeLessThanEqualOrderByNotBeforeAsc(
                        clock.instant(), PageRequest.of(0, 50))) {
            try {
                cleanOne(job.getKey());
            } catch (RuntimeException exception) {
                log.warn(
                        "Profile image cleanup transaction failed: type={}",
                        exception.getClass().getSimpleName());
            }
        }
    }

    public void cleanOne(String key) {
        transactions.executeWithoutResult(
                status ->
                        jobs.findForUpdate(key)
                                .ifPresent(
                                        job -> {
                                            if (job.getNotBefore().isAfter(clock.instant())) return;
                                            // Never delete a currently referenced object, even
                                            // after recovery races.
                                            if (members.existsByProfileImageUrl(job.getUrl())) {
                                                jobs.delete(job);
                                                return;
                                            }
                                            try {
                                                images.deleteProfileImage(job.getKey());
                                                jobs.delete(job);
                                            } catch (RuntimeException failure) {
                                                job.retryAt(
                                                        clock.instant()
                                                                .plus(
                                                                        Duration.ofMinutes(
                                                                                Math.min(
                                                                                        60,
                                                                                        1L
                                                                                                << Math
                                                                                                        .min(
                                                                                                                job
                                                                                                                        .getAttempts(),
                                                                                                                6)))));
                                                log.warn(
                                                        "Profile image cleanup deferred: attempt={}, type={}",
                                                        job.getAttempts(),
                                                        failure.getClass().getSimpleName());
                                            }
                                        }));
    }
}
