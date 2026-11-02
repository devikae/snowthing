package com.ikae.snowthing.domain.image.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
public class MarketThumbnailService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ImageService images;
    private final ImageUrlResolver urls;
    private final ImageProcessingGate gate;
    private final S3Client s3;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final com.ikae.snowthing.global.security.SharedRequestLimiter limits;

    @Value("${cloud.aws.s3.bucket:snowthing-media-00001}")
    private String bucket;

    private record Job(String source, String ready, long revision, boolean orphan) {}

    public MarketThumbnailService(
            JdbcTemplate jdbc,
            ImageService images,
            ImageUrlResolver urls,
            ImageProcessingGate gate,
            S3Client s3,
            Clock clock,
            PlatformTransactionManager manager,
            com.ikae.snowthing.global.security.SharedRequestLimiter limits) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.images = images;
        this.urls = urls;
        this.gate = gate;
        this.s3 = s3;
        this.clock = clock;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.limits = limits;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void queue(String postId, String firstImage) {
        String key = urls.toStorageValue(firstImage);
        // Old uploads already have thumbnails. New uploads create none until selected here.
        if (key != null && !key.startsWith("public/posts/originals/m-")) key = null;
        jdbc.update(
                "INSERT INTO market_thumbnail(post_public_id,source_key,revision,due_at) VALUES(?,?,0,?) ON DUPLICATE KEY UPDATE source_key=VALUES(source_key),revision=revision+1,due_at=VALUES(due_at)",
                postId,
                key,
                Timestamp.from(clock.instant()));
    }

    public Map<String, String> readyUrls(List<String> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        var result = new HashMap<String, String>();
        var params = Collections.singletonMap("postIds", postIds);
        namedJdbc.query(
                "SELECT post_public_id FROM market_thumbnail WHERE source_key IS NOT NULL AND source_key=ready_source_key AND post_public_id IN (:postIds)",
                params,
                row -> {
                    String id = row.getString(1);
                    result.put(id, urls.toPublicUrl(objectKey(id)));
                });
        return Map.copyOf(result);
    }

    private String objectKey(String id) {
        return "public/posts/thumbnails/market-" + id + ".jpg";
    }

    @Scheduled(fixedDelayString = "${snowthing.market-thumbnails.delay-ms:5000}")
    public void process() {
        for (String id :
                jdbc.queryForList(
                        "SELECT post_public_id FROM market_thumbnail WHERE due_at IS NOT NULL AND due_at<=? AND (lease_until IS NULL OR lease_until<=?) ORDER BY due_at LIMIT 20",
                        String.class,
                        Timestamp.from(clock.instant()),
                        Timestamp.from(clock.instant()))) {
            try {
                processOne(id);
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("Market thumbnail deferred: {}", failure.getClass().getSimpleName());
            }
        }
    }

    public void processOne(String id) {
        Job job =
                tx.execute(
                        status -> {
                            boolean orphan =
                                    jdbc.queryForObject(
                                                    "SELECT COUNT(*) FROM post WHERE public_id=?",
                                                    Long.class,
                                                    id)
                                            == 0;
                            var rows =
                                    jdbc.query(
                                            "SELECT source_key,ready_source_key,revision,due_at,lease_until FROM market_thumbnail WHERE post_public_id=? FOR UPDATE",
                                            (row, index) -> {
                                                var due = row.getTimestamp(4);
                                                var lease = row.getTimestamp(5);
                                                if ((!orphan
                                                                && (due == null
                                                                        || due.toInstant()
                                                                                .isAfter(
                                                                                        clock
                                                                                                .instant())))
                                                        || (lease != null
                                                                && lease.toInstant()
                                                                        .isAfter(clock.instant())))
                                                    return null;
                                                return new Job(
                                                        orphan ? null : row.getString(1),
                                                        row.getString(2),
                                                        row.getLong(3),
                                                        orphan);
                                            },
                                            id);
                            if (rows.isEmpty() || rows.get(0) == null) return null;
                            Job selected = rows.get(0);
                            jdbc.update(
                                    "UPDATE market_thumbnail SET lease_until=? WHERE post_public_id=?",
                                    Timestamp.from(clock.instant().plus(Duration.ofMinutes(5))),
                                    id);
                            return selected;
                        });
        if (job == null) return;
        try {
            if (job.source() != null && !Objects.equals(job.source(), job.ready())) {
                var decision =
                        limits.acquire(
                                List.of(
                                        new com.ikae.snowthing.global.security.SharedRequestLimiter
                                                .Limit(
                                                "snowthing:limits:market-thumbnails:hour",
                                                300,
                                                Duration.ofHours(1)),
                                        new com.ikae.snowthing.global.security.SharedRequestLimiter
                                                .Limit(
                                                "snowthing:limits:market-thumbnails:day",
                                                1000,
                                                Duration.ofDays(1))));
                if (!decision.allowed())
                    throw new com.ikae.snowthing.global.exception.CustomException(
                            com.ikae.snowthing.global.error.ErrorCode.PROFILE_ACTION_LIMIT);
                images.createMarketThumbnail(job.source(), objectKey(id), gate);
            } else if (job.source() == null)
                s3.deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket(bucket)
                                .key(objectKey(id))
                                .overrideConfiguration(
                                        c -> c.apiCallTimeout(Duration.ofSeconds(20)))
                                .build());
            tx.executeWithoutResult(
                    status -> {
                        if (job.orphan()) {
                            jdbc.update(
                                    "DELETE FROM market_thumbnail WHERE post_public_id=? AND revision=?",
                                    id,
                                    job.revision());
                            return;
                        }
                        int updated =
                                jdbc.update(
                                        "UPDATE market_thumbnail SET ready_source_key=?,lease_until=NULL,due_at=NULL WHERE post_public_id=? AND revision=?",
                                        job.source(),
                                        id,
                                        job.revision());
                        if (updated == 0)
                            jdbc.update(
                                    "UPDATE market_thumbnail SET lease_until=NULL,due_at=? WHERE post_public_id=?",
                                    Timestamp.from(clock.instant()),
                                    id);
                    });
        } catch (RuntimeException failure) {
            tx.executeWithoutResult(
                    status ->
                            jdbc.update(
                                    "UPDATE market_thumbnail SET lease_until=NULL,due_at=? WHERE post_public_id=?",
                                    Timestamp.from(clock.instant().plus(Duration.ofMinutes(1))),
                                    id));
            throw failure;
        }
    }

    @Scheduled(fixedDelayString = "${snowthing.market-thumbnails.orphan-delay-ms:3600000}")
    public void cleanupOrphans() {
        for (String id :
                jdbc.queryForList(
                        "SELECT t.post_public_id FROM market_thumbnail t LEFT JOIN post p ON p.public_id=t.post_public_id WHERE p.post_id IS NULL AND (t.lease_until IS NULL OR t.lease_until<=?) LIMIT 20",
                        String.class,
                        Timestamp.from(clock.instant()))) {
            try {
                processOne(id);
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn(
                                "Orphan market thumbnail deferred: {}",
                                failure.getClass().getSimpleName());
            }
        }
    }
}
