package com.ikae.snowthing.domain.slopestatus.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.slopestatus.dto.SlopeStatusBoardResponse;
import com.ikae.snowthing.domain.slopestatus.dto.SlopeStatusBoardResponse.ResortSlopes;
import com.ikae.snowthing.domain.slopestatus.dto.SlopeStatusBoardResponse.Slope;
import com.ikae.snowthing.domain.slopestatus.entity.CollectResult;
import com.ikae.snowthing.domain.slopestatus.entity.ResortSlopeStatus;
import com.ikae.snowthing.domain.slopestatus.repository.ResortSlopeStatusRepository;
import com.ikae.snowthing.domain.slopestatus.source.SlopeEntry;
import com.ikae.snowthing.domain.slopestatus.source.SlopeStatusSourceRegistry;
import com.ikae.snowthing.domain.slopestatus.source.SlopeStatusSourceRegistry.SlopeSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlopeStatusQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ResortRepository resortRepository;
    private final ResortSlopeStatusRepository statusRepository;
    private final SlopeStatusSourceRegistry registry;
    private final ObjectMapper objectMapper;

    /** 쿼리 두 번: 활성 리조트 목록, 그 리조트들의 현황 줄. */
    public SlopeStatusBoardResponse getBoard() {
        List<Resort> resorts = resortRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
        Map<Long, ResortSlopeStatus> statuses =
                statusRepository
                        .findAllByResortIdIn(resorts.stream().map(Resort::getId).toList())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        status -> status.getResort().getId(), Function.identity()));
        return new SlopeStatusBoardResponse(
                resorts.stream()
                        .map(resort -> toResponse(resort, statuses.get(resort.getId())))
                        .toList());
    }

    private ResortSlopes toResponse(Resort resort, ResortSlopeStatus status) {
        Optional<SlopeSource> source = registry.sourceFor(resort.getCode());
        if (source.isEmpty()) {
            return new ResortSlopes(
                    resort.getCode(),
                    resort.getName(),
                    resort.getRegionName(),
                    false,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    List.of());
        }
        if (status == null) {
            return new ResortSlopes(
                    resort.getCode(),
                    resort.getName(),
                    resort.getRegionName(),
                    true,
                    source.get().pageUrl(),
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    List.of());
        }
        boolean hasSnapshot = status.getCollectedAt() != null;
        return new ResortSlopes(
                resort.getCode(),
                resort.getName(),
                resort.getRegionName(),
                true,
                status.getSourceUrl(),
                kst(status.getCollectedAt()),
                kst(status.getLastAttemptAt()),
                hasSnapshot && status.getLastResult() == CollectResult.FAILED,
                status.getOpenSlopeCount(),
                status.getTotalSlopeCount(),
                status.getOperatingNote(),
                hasSnapshot ? slopes(resort.getCode(), status.getSlopesJson()) : List.of());
    }

    private List<Slope> slopes(String resortCode, String slopesJson) {
        if (slopesJson == null) {
            return List.of();
        }
        try {
            SlopeEntry[] entries = objectMapper.readValue(slopesJson, SlopeEntry[].class);
            return Arrays.stream(entries)
                    .map(
                            entry ->
                                    new Slope(
                                            entry.name(),
                                            entry.difficulty(),
                                            entry.hasOpenSession(),
                                            entry.day(),
                                            entry.night(),
                                            entry.lateNight(),
                                            entry.note()))
                    .toList();
        } catch (JacksonException exception) {
            log.warn("저장된 슬로프 현황 JSON을 읽지 못했습니다. resortCode={}", resortCode);
            return List.of();
        }
    }

    private static OffsetDateTime kst(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
    }
}
