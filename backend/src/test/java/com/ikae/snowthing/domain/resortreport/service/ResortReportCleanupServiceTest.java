package com.ikae.snowthing.domain.resortreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ikae.snowthing.domain.resortreport.repository.ResortReportRepository;

@ExtendWith(MockitoExtension.class)
class ResortReportCleanupServiceTest {

    @Mock private ResortReportRepository resortReportRepository;

    @Test
    void deletesReportsBeforeCurrentKstMonth() {
        Clock clock = Clock.fixed(Instant.parse("2027-01-31T19:40:00Z"), ZoneId.of("Asia/Seoul"));
        ResortReportCleanupService service =
                new ResortReportCleanupService(resortReportRepository, clock);
        LocalDateTime cutoff = LocalDateTime.of(2027, 2, 1, 0, 0);
        given(resortReportRepository.deleteCreatedBefore(cutoff)).willReturn(12);

        int deletedCount = service.deletePreviousMonthReports();

        assertThat(deletedCount).isEqualTo(12);
        verify(resortReportRepository).deleteCreatedBefore(cutoff);
    }
}
