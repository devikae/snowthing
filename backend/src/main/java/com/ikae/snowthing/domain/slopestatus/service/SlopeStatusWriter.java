package com.ikae.snowthing.domain.slopestatus.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.slopestatus.entity.ResortSlopeStatus;
import com.ikae.snowthing.domain.slopestatus.repository.ResortSlopeStatusRepository;
import com.ikae.snowthing.domain.slopestatus.source.SlopeSnapshot;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/** 수집 결과 저장. 외부 호출이 끝난 뒤에 짧은 트랜잭션으로 한 리조트의 한 줄만 바꾼다. */
@Component
@RequiredArgsConstructor
public class SlopeStatusWriter {

    private final ResortSlopeStatusRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveSuccess(
            Resort resort, String sourceUrl, SlopeSnapshot snapshot, LocalDateTime now) {
        String slopesJson = objectMapper.writeValueAsString(snapshot.slopes());
        int openCount = snapshot.openCount();
        int totalCount = snapshot.slopes().size();
        repository
                .findByResortId(resort.getId())
                .ifPresentOrElse(
                        status ->
                                status.recordSuccess(
                                        sourceUrl,
                                        slopesJson,
                                        openCount,
                                        totalCount,
                                        snapshot.operatingNote(),
                                        now),
                        () ->
                                repository.save(
                                        ResortSlopeStatus.succeeded(
                                                resort,
                                                sourceUrl,
                                                slopesJson,
                                                openCount,
                                                totalCount,
                                                snapshot.operatingNote(),
                                                now)));
    }

    /** 마지막 성공 값은 건드리지 않는다. */
    @Transactional
    public void saveFailure(Resort resort, String sourceUrl, String reason, LocalDateTime now) {
        repository
                .findByResortId(resort.getId())
                .ifPresentOrElse(
                        status -> status.recordFailure(reason, now),
                        () ->
                                repository.save(
                                        ResortSlopeStatus.failed(resort, sourceUrl, reason, now)));
    }
}
