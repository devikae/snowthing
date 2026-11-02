package com.ikae.snowthing.domain.admin.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.admin.dto.AdminDashboardStatsResponse;
import com.ikae.snowthing.domain.admin.dto.DailyVisitorStatDto;
import com.ikae.snowthing.domain.admin.entity.DailyVisitorStats;
import com.ikae.snowthing.domain.admin.repository.DailyVisitorLogRepository;
import com.ikae.snowthing.domain.admin.repository.DailyVisitorStatsRepository;
import com.ikae.snowthing.domain.member.repository.MemberRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class VisitorStatsService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

    private final DailyVisitorLogRepository visitorLogRepository;
    private final DailyVisitorStatsRepository visitorStatsRepository;
    private final MemberRepository memberRepository;
    private final SystemConfigService systemConfigService;
    private final TransactionTemplate transactionTemplate;

    private final Cache<String, Boolean> recentVisitorsCache =
            Caffeine.newBuilder()
                    .maximumSize(50_000)
                    .expireAfterWrite(Duration.ofMinutes(30))
                    .build();

    public VisitorStatsService(
            DailyVisitorLogRepository visitorLogRepository,
            DailyVisitorStatsRepository visitorStatsRepository,
            MemberRepository memberRepository,
            SystemConfigService systemConfigService,
            PlatformTransactionManager transactionManager) {
        this.visitorLogRepository = visitorLogRepository;
        this.visitorStatsRepository = visitorStatsRepository;
        this.memberRepository = memberRepository;
        this.systemConfigService = systemConfigService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void recordVisit(String clientIp, String userAgent, boolean isMember) {
        if (clientIp == null || clientIp.isBlank()) return;

        LocalDate today = LocalDate.now(ZONE_KST);
        String rawKey = clientIp + "|" + (userAgent != null ? userAgent : "") + "|" + today;
        String hash = sha256(rawKey);

        // Fast in-memory filter: already recorded recently
        if (recentVisitorsCache.getIfPresent(hash) != null) {
            try {
                transactionTemplate.executeWithoutResult(
                        status -> visitorStatsRepository.incrementPv(today));
            } catch (Exception e) {
                log.debug("PV increment failed: {}", e.getMessage());
            }
            return;
        }

        recentVisitorsCache.put(hash, Boolean.TRUE);

        try {
            transactionTemplate.executeWithoutResult(
                    status -> {
                        int inserted =
                                visitorLogRepository.insertIgnore(
                                        today, hash, isMember, LocalDateTime.now());
                        if (inserted > 0) {
                            visitorStatsRepository.incrementVisitor(today, isMember);
                        } else {
                            visitorStatsRepository.incrementPv(today);
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to record visitor log: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getDashboardStats() {
        LocalDate today = LocalDate.now(ZONE_KST);
        LocalDate startDay = today.minusDays(29);

        DailyVisitorStats todayStats = visitorStatsRepository.findById(today).orElse(null);
        int todayTotal = todayStats != null ? todayStats.getTotalVisitors() : 0;
        int todayMember = todayStats != null ? todayStats.getMemberVisitors() : 0;
        int todayGuest = todayStats != null ? todayStats.getGuestVisitors() : 0;
        int todayPv = todayStats != null ? todayStats.getPvCount() : 0;

        long totalMembers = memberRepository.count();
        long todayNewMembers = memberRepository.countByCreatedAtAfter(today.atStartOfDay());
        boolean anonymousChat = systemConfigService.isAnonymousChatEnabled();

        List<DailyVisitorStats> statsList =
                visitorStatsRepository.findByStatDateBetweenOrderByStatDateAsc(startDay, today);
        Map<LocalDate, DailyVisitorStats> statsMap =
                statsList.stream()
                        .collect(Collectors.toMap(DailyVisitorStats::getStatDate, Function.identity()));

        List<DailyVisitorStatDto> trends = new ArrayList<>();
        for (LocalDate d = startDay; !d.isAfter(today); d = d.plusDays(1)) {
            DailyVisitorStats s = statsMap.get(d);
            trends.add(
                    new DailyVisitorStatDto(
                            d,
                            s != null ? s.getTotalVisitors() : 0,
                            s != null ? s.getMemberVisitors() : 0,
                            s != null ? s.getGuestVisitors() : 0,
                            s != null ? s.getPvCount() : 0));
        }

        return new AdminDashboardStatsResponse(
                todayTotal,
                todayMember,
                todayGuest,
                todayPv,
                totalMembers,
                todayNewMembers,
                anonymousChat,
                trends);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
