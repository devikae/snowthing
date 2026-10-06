package com.ikae.snowthing.domain.resortreport.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportCreateRequest;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportResponse;
import com.ikae.snowthing.domain.resortreport.entity.ResortReport;
import com.ikae.snowthing.domain.resortreport.repository.ResortReportRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResortReportService {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ResortReportRepository resortReportRepository;
    private final ResortRepository resortRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public ResortReportResponse createReport(Long memberId, ResortReportCreateRequest request) {
        Member author =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        Resort resort =
                resortRepository
                        .findById(request.getResortId())
                        .filter(Resort::isActive)
                        .orElseThrow(() -> new CustomException(ErrorCode.RESORT_NOT_FOUND));

        ResortReport report =
                ResortReport.builder()
                        .resort(resort)
                        .author(author)
                        .content(request.getContent())
                        .createdAt(LocalDateTime.now())
                        .build();

        ResortReport savedReport = resortReportRepository.save(report);
        return ResortReportResponse.from(savedReport);
    }

    public List<ResortReportResponse> getTodayReports(Long resortId, Integer limit) {
        LocalDateTime todayStart = LocalDate.now(KST_ZONE).atStartOfDay();
        int safeLimit = sanitizeLimit(limit);
        Pageable pageable = PageRequest.of(0, safeLimit);

        List<ResortReport> reports;
        if (resortId != null) {
            reports =
                    resortReportRepository.findTodayReportsByResortId(
                            resortId, todayStart, pageable);
        } else {
            reports = resortReportRepository.findTodayReports(todayStart, pageable);
        }

        return reports.stream().map(ResortReportResponse::from).toList();
    }

    private int sanitizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
