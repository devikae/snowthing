package com.ikae.snowthing.domain.resortreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportCreateRequest;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportResponse;
import com.ikae.snowthing.domain.resortreport.entity.ResortReport;
import com.ikae.snowthing.domain.resortreport.entity.ResortReportStatus;
import com.ikae.snowthing.domain.resortreport.repository.ResortReportRepository;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

@ExtendWith(MockitoExtension.class)
class ResortReportServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock WINTER_CLOCK =
            Clock.fixed(Instant.parse("2026-12-06T06:30:00Z"), KST);

    @Mock private ResortReportRepository resortReportRepository;
    @Mock private ResortRepository resortRepository;
    @Mock private MemberRepository memberRepository;

    private ResortReportService resortReportService;

    @BeforeEach
    void setUp() {
        resortReportService =
                new ResortReportService(
                        resortReportRepository, resortRepository, memberRepository, WINTER_CLOCK);
    }

    @Test
    @DisplayName("회원 행 잠금 후 당일 제보 수를 확인하고 제보를 저장한다")
    void createReportSuccess() {
        Member author = member(1L, "눈꽃보더", Role.ROLE_USER);
        Resort resort = resort(10L);
        ResortReportCreateRequest request = request(resort.getId());
        given(memberRepository.findByIdForUpdate(author.getId())).willReturn(Optional.of(author));
        given(resortRepository.findById(resort.getId())).willReturn(Optional.of(resort));
        given(resortReportRepository.save(any(ResortReport.class)))
                .willAnswer(
                        invocation -> {
                            ResortReport saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 100L);
                            return saved;
                        });

        ResortReportResponse response = resortReportService.createReport(author.getId(), request);

        assertThat(response.getReportId()).isEqualTo(100L);
        assertThat(response.getCreatedAt().getOffset().getTotalSeconds()).isEqualTo(9 * 60 * 60);
        assertThat(response.isCanDelete()).isTrue();
        verify(memberRepository).findByIdForUpdate(author.getId());
        verify(resortReportRepository)
                .countByAuthorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(author.getId()), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("삭제 여부와 무관한 당일 제보 수가 5개면 등록을 거절한다")
    void createReportRejectsDailyLimit() {
        Member author = member(1L, "눈꽃보더", Role.ROLE_USER);
        given(memberRepository.findByIdForUpdate(author.getId())).willReturn(Optional.of(author));
        given(
                        resortReportRepository
                                .countByAuthorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                        eq(author.getId()), any(), any()))
                .willReturn(5L);

        assertThatThrownBy(() -> resortReportService.createReport(author.getId(), request(10L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESORT_REPORT_DAILY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("5월부터 9월까지는 제보 등록을 거절한다")
    void createReportRejectsOffSeason() {
        ResortReportService summerService =
                new ResortReportService(
                        resortReportRepository,
                        resortRepository,
                        memberRepository,
                        Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), KST));

        assertThatThrownBy(() -> summerService.createReport(1L, request(10L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESORT_REPORT_SEASON_CLOSED);
    }

    @Test
    @DisplayName("10월에는 시즌 준비 제보를 등록할 수 있다")
    void createReportAllowsOctober() {
        ResortReportService octoberService =
                new ResortReportService(
                        resortReportRepository,
                        resortRepository,
                        memberRepository,
                        Clock.fixed(Instant.parse("2026-10-06T15:30:00Z"), KST));
        Member author = member(1L, "눈꽃보더", Role.ROLE_USER);
        Resort resort = resort(10L);
        given(memberRepository.findByIdForUpdate(author.getId())).willReturn(Optional.of(author));
        given(resortRepository.findById(resort.getId())).willReturn(Optional.of(resort));
        given(resortReportRepository.save(any(ResortReport.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ResortReportResponse response =
                octoberService.createReport(author.getId(), request(resort.getId()));

        assertThat(response.getContent()).isEqualTo("설질이 좋아요");
    }

    @Test
    @DisplayName("오늘의 공개 제보를 페이지 정보와 삭제 권한을 포함해 반환한다")
    void getTodayReports() {
        Member author = member(1L, "눈꽃보더", Role.ROLE_USER);
        ResortReport report = report(1L, author, resort(10L));
        given(
                        resortReportRepository.findTodayReports(
                                eq(ResortReportStatus.NORMAL), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(report)));

        CursorPageResponse<ResortReportResponse> result =
                resortReportService.getTodayReports(null, 1, 20, new CustomUserDetails(author));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().isCanDelete()).isTrue();
        assertThat(result.pageInfo().totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("작성자는 자신의 제보를 소프트 삭제할 수 있다")
    void authorCanSoftDelete() {
        Member author = member(1L, "눈꽃보더", Role.ROLE_USER);
        ResortReport report = report(1L, author, resort(10L));
        given(resortReportRepository.findByIdWithAuthor(report.getId()))
                .willReturn(Optional.of(report));

        resortReportService.deleteReport(report.getId(), new CustomUserDetails(author));

        assertThat(report.getStatus()).isEqualTo(ResortReportStatus.DELETED);
        assertThat(report.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 회원은 제보를 삭제할 수 없다")
    void otherMemberCannotDelete() {
        Member author = member(1L, "작성자", Role.ROLE_USER);
        Member other = member(2L, "다른회원", Role.ROLE_USER);
        ResortReport report = report(1L, author, resort(10L));
        given(resortReportRepository.findByIdWithAuthor(report.getId()))
                .willReturn(Optional.of(report));

        assertThatThrownBy(
                        () ->
                                resortReportService.deleteReport(
                                        report.getId(), new CustomUserDetails(other)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("관리자는 다른 회원의 제보를 삭제할 수 있다")
    void adminCanDelete() {
        Member author = member(1L, "작성자", Role.ROLE_USER);
        Member admin = member(2L, "관리자", Role.ROLE_ADMIN);
        ResortReport report = report(1L, author, resort(10L));
        given(resortReportRepository.findByIdWithAuthor(report.getId()))
                .willReturn(Optional.of(report));

        resortReportService.deleteReport(report.getId(), new CustomUserDetails(admin));

        assertThat(report.getStatus()).isEqualTo(ResortReportStatus.DELETED);
    }

    @ParameterizedTest
    @EnumSource(
            value = ResortReportStatus.class,
            names = {"NORMAL", "HIDDEN", "BLOCKED"})
    @DisplayName("An admin can set every allowed moderation status")
    void adminCanSetAllowedModerationStatus(ResortReportStatus status) {
        Member author = member(1L, "작성자", Role.ROLE_USER);
        Member admin = member(2L, "관리자", Role.ROLE_ADMIN);
        ResortReport report = report(1L, author, resort(10L));
        given(resortReportRepository.findByIdWithAuthor(report.getId()))
                .willReturn(Optional.of(report));

        resortReportService.updateModerationStatus(
                report.getId(), status, new CustomUserDetails(admin));

        assertThat(report.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("A non-admin receives ACCESS_DENIED when changing moderation status")
    void nonAdminCannotChangeModerationStatus() {
        Member member = member(1L, "member", Role.ROLE_USER);

        assertThatThrownBy(
                        () ->
                                resortReportService.updateModerationStatus(
                                        1L,
                                        ResortReportStatus.DELETED,
                                        new CustomUserDetails(member)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("An admin receives INVALID_INPUT when requesting DELETED moderation status")
    void adminCannotSetDeletedModerationStatus() {
        Member admin = member(1L, "admin", Role.ROLE_ADMIN);

        assertThatThrownBy(
                        () ->
                                resortReportService.updateModerationStatus(
                                        1L,
                                        ResortReportStatus.DELETED,
                                        new CustomUserDetails(admin)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private ResortReportCreateRequest request(Long resortId) {
        return ResortReportCreateRequest.builder().resortId(resortId).content("설질이 좋아요").build();
    }

    private ResortReport report(Long id, Member author, Resort resort) {
        ResortReport report =
                ResortReport.builder()
                        .resort(resort)
                        .author(author)
                        .content("설질이 좋아요")
                        .createdAt(LocalDateTime.of(2026, 12, 6, 15, 30))
                        .build();
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    private Resort resort(Long id) {
        Resort resort =
                Resort.builder()
                        .name("하이원")
                        .code("HIGH1")
                        .regionName("강원")
                        .displayOrder(1)
                        .active(true)
                        .build();
        ReflectionTestUtils.setField(resort, "id", id);
        return resort;
    }

    private Member member(Long id, String nickname, Role role) {
        Member member =
                Member.builder()
                        .email(nickname + "@snowthing.com")
                        .nickname(nickname)
                        .password("hashed")
                        .role(role)
                        .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
