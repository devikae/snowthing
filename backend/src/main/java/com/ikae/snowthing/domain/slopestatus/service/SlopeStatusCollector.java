package com.ikae.snowthing.domain.slopestatus.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.slopestatus.config.SlopeStatusProperties;
import com.ikae.snowthing.domain.slopestatus.source.SlopeFetchException;
import com.ikae.snowthing.domain.slopestatus.source.SlopeSnapshot;
import com.ikae.snowthing.domain.slopestatus.source.SlopeStatusHttpClient;
import com.ikae.snowthing.domain.slopestatus.source.SlopeStatusSourceRegistry;
import com.ikae.snowthing.domain.slopestatus.source.SlopeStatusSourceRegistry.SlopeSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 켜 둔 리조트의 공식 현황을 읽어 저장한다. 리조트를 하나씩 차례로 부른다(동시에 여러 사이트를 두드리지 않는다). 한 리조트의 실패가 다른 리조트 수집을 막지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlopeStatusCollector {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SlopeStatusProperties properties;
    private final SlopeStatusSourceRegistry registry;
    private final SlopeStatusHttpClient httpClient;
    private final ResortRepository resortRepository;
    private final SlopeStatusWriter writer;
    private final Clock clock;

    /** 켜 둔 리조트를 코드 순서로 수집하고, 시도한 리조트 수를 돌려준다. */
    public int collectEnabled() {
        int attempted = 0;
        for (String resortCode : properties.enabledResortCodes().stream().sorted().toList()) {
            if (collect(resortCode)) {
                attempted++;
            }
        }
        return attempted;
    }

    /** 출처가 없거나 비활성 리조트면 시도하지 않고 false. */
    boolean collect(String resortCode) {
        Optional<SlopeSource> source = registry.sourceFor(resortCode);
        Optional<Resort> resort = resortRepository.findByCode(resortCode).filter(Resort::isActive);
        if (source.isEmpty() || resort.isEmpty()) {
            log.warn("슬로프 현황 수집 대상이 아닙니다. resortCode={}", resortCode);
            return false;
        }
        SlopeSource slopeSource = source.get();
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));
        try {
            SlopeSnapshot snapshot =
                    slopeSource.parser().apply(httpClient.get(slopeSource.fetchUri()));
            writer.saveSuccess(resort.get(), slopeSource.pageUrl(), snapshot, now);
            log.info(
                    "슬로프 현황 수집 성공. resortCode={}, open={}/{}",
                    resortCode,
                    snapshot.openCount(),
                    snapshot.slopes().size());
        } catch (DataIntegrityViolationException exception) {
            // 서버 두 대가 같은 리조트의 첫 줄을 동시에 만들면 유니크 제약이 한쪽을 막는다. 다음 회차에 갱신된다.
            log.warn("슬로프 현황 첫 저장 충돌. resortCode={}", resortCode);
        } catch (RuntimeException exception) {
            String reason =
                    exception instanceof SlopeFetchException
                            ? exception.getMessage()
                            : "파싱 실패: " + exception.getClass().getSimpleName();
            log.warn("슬로프 현황 수집 실패. resortCode={}, reason={}", resortCode, reason);
            writer.saveFailure(resort.get(), slopeSource.pageUrl(), reason, now);
        }
        return true;
    }
}
