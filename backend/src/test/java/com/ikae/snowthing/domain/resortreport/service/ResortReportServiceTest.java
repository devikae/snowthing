package com.ikae.snowthing.domain.resortreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

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

@ExtendWith(MockitoExtension.class)
class ResortReportServiceTest {

    @Mock private ResortReportRepository resortReportRepository;
    @Mock private ResortRepository resortRepository;
    @Mock private MemberRepository memberRepository;

    @InjectMocks private ResortReportService resortReportService;

    private Resort createMockResort(Long id, String name, String code, boolean active) {
        Resort resort =
                Resort.builder()
                        .name(name)
                        .code(code)
                        .regionName("강원")
                        .displayOrder(1)
                        .active(active)
                        .build();
        ReflectionTestUtils.setField(resort, "id", id);
        return resort;
    }

    private Member createMockMember(Long id, String email, String nickname) {
        Member member =
                Member.builder()
                        .email(email)
                        .nickname(nickname)
                        .password("hashed_pw")
                        .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("정상 제보 등록 시 저장 및 응답 DTO 반환")
    void createReport_Success() {
        // given
        Long memberId = 1L;
        Long resortId = 10L;
        ResortReportCreateRequest request =
                ResortReportCreateRequest.builder()
                        .resortId(resortId)
                        .content("하이원 마운틴탑 눈 진짜 좋아요!")
                        .build();

        Member author = createMockMember(memberId, "rider@snowthing.com", "눈꽃보더");
        Resort resort = createMockResort(resortId, "하이원", "HIGH1", true);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(author));
        given(resortRepository.findById(resortId)).willReturn(Optional.of(resort));

        ResortReport savedReport =
                ResortReport.builder()
                        .resort(resort)
                        .author(author)
                        .content("하이원 마운틴탑 눈 진짜 좋아요!")
                        .createdAt(LocalDateTime.of(2026, 10, 6, 15, 30))
                        .build();
        ReflectionTestUtils.setField(savedReport, "id", 100L);

        given(resortReportRepository.save(any(ResortReport.class))).willReturn(savedReport);

        // when
        ResortReportResponse response = resortReportService.createReport(memberId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getReportId()).isEqualTo(100L);
        assertThat(response.getResortName()).isEqualTo("하이원");
        assertThat(response.getResortCode()).isEqualTo("HIGH1");
        assertThat(response.getAuthorNickname()).isEqualTo("눈꽃보더");
        assertThat(response.getContent()).isEqualTo("하이원 마운틴탑 눈 진짜 좋아요!");
        verify(resortReportRepository).save(any(ResortReport.class));
    }

    @Test
    @DisplayName("존재하지 않는 회원이 제보 등록 시 MEMBER_NOT_FOUND 예외 발생")
    void createReport_MemberNotFound() {
        // given
        Long memberId = 999L;
        ResortReportCreateRequest request =
                ResortReportCreateRequest.builder()
                        .resortId(1L)
                        .content("설질 제보 테스트")
                        .build();

        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resortReportService.createReport(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않거나 비활성화된 리조트 제보 등록 시 RESORT_NOT_FOUND 예외 발생")
    void createReport_ResortNotFound() {
        // given
        Long memberId = 1L;
        Long resortId = 999L;
        ResortReportCreateRequest request =
                ResortReportCreateRequest.builder()
                        .resortId(resortId)
                        .content("설질 제보 테스트")
                        .build();

        Member author = createMockMember(memberId, "rider@snowthing.com", "눈꽃보더");
        given(memberRepository.findById(memberId)).willReturn(Optional.of(author));
        given(resortRepository.findById(resortId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resortReportService.createReport(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESORT_NOT_FOUND);
    }

    @Test
    @DisplayName("빈 본문 또는 공백만 있는 제보 내용 등록 시 INVALID_RESORT_REPORT_CONTENT 예외 발생")
    void createReport_EmptyContent() {
        // given
        Long memberId = 1L;
        Long resortId = 1L;
        ResortReportCreateRequest request =
                ResortReportCreateRequest.builder().resortId(resortId).content("   ").build();

        Member author = createMockMember(memberId, "rider@snowthing.com", "눈꽃보더");
        Resort resort = createMockResort(resortId, "하이원", "HIGH1", true);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(author));
        given(resortRepository.findById(resortId)).willReturn(Optional.of(resort));

        // when & then
        assertThatThrownBy(() -> resortReportService.createReport(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_RESORT_REPORT_CONTENT);
    }

    @Test
    @DisplayName("100자를 초과하는 제보 내용 등록 시 INVALID_RESORT_REPORT_CONTENT 예외 발생")
    void createReport_TooLongContent() {
        // given
        Long memberId = 1L;
        Long resortId = 1L;
        String longContent = "A".repeat(101);
        ResortReportCreateRequest request =
                ResortReportCreateRequest.builder()
                        .resortId(resortId)
                        .content(longContent)
                        .build();

        Member author = createMockMember(memberId, "rider@snowthing.com", "눈꽃보더");
        Resort resort = createMockResort(resortId, "하이원", "HIGH1", true);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(author));
        given(resortRepository.findById(resortId)).willReturn(Optional.of(resort));

        // when & then
        assertThatThrownBy(() -> resortReportService.createReport(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_RESORT_REPORT_CONTENT);
    }

    @Test
    @DisplayName("오늘의 설질 전체 목록 조회")
    void getTodayReports_All() {
        // given
        Member author = createMockMember(1L, "rider@snowthing.com", "눈꽃보더");
        Resort resort = createMockResort(1L, "하이원", "HIGH1", true);
        ResortReport report =
                ResortReport.builder()
                        .resort(resort)
                        .author(author)
                        .content("설질 굿")
                        .createdAt(LocalDateTime.now())
                        .build();
        ReflectionTestUtils.setField(report, "id", 1L);

        given(resortReportRepository.findTodayReports(any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(report));

        // when
        List<ResortReportResponse> results = resortReportService.getTodayReports(null, 10);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getContent()).isEqualTo("설질 굿");
        verify(resortReportRepository).findTodayReports(any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    @DisplayName("특정 리조트 필터로 오늘의 설질 목록 조회")
    void getTodayReports_ByResortId() {
        // given
        Long resortId = 2L;
        Member author = createMockMember(1L, "rider@snowthing.com", "눈꽃보더");
        Resort resort = createMockResort(resortId, "용평", "YONGPYONG", true);
        ResortReport report =
                ResortReport.builder()
                        .resort(resort)
                        .author(author)
                        .content("레인보우 설질 최상")
                        .createdAt(LocalDateTime.now())
                        .build();
        ReflectionTestUtils.setField(report, "id", 2L);

        given(
                        resortReportRepository.findTodayReportsByResortId(
                                eq(resortId), any(LocalDateTime.class), any(Pageable.class)))
                .willReturn(List.of(report));

        // when
        List<ResortReportResponse> results = resortReportService.getTodayReports(resortId, 10);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getResortName()).isEqualTo("용평");
        verify(resortReportRepository)
                .findTodayReportsByResortId(eq(resortId), any(LocalDateTime.class), any(Pageable.class));
    }
}
