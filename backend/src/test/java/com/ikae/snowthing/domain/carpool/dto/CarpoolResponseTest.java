package com.ikae.snowthing.domain.carpool.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.post.entity.Post;

class CarpoolResponseTest {

    @Test
    void includesEquipmentLoadAvailability() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 3, 12, 0);
        Post post = mock(Post.class);
        Member member = mock(Member.class);
        Resort resort = mock(Resort.class);
        when(post.getPublicId()).thenReturn("post-public-id");
        when(post.getTitle()).thenReturn("카풀 모집");
        when(post.getContent()).thenReturn("장비 적재 가능합니다.");
        when(post.getMember()).thenReturn(member);
        when(post.getCreatedAt()).thenReturn(now);
        when(member.getNickname()).thenReturn("작성자");
        when(resort.getId()).thenReturn(1L);
        when(resort.getName()).thenReturn("테스트 리조트");

        CarpoolDetail detail =
                CarpoolDetail.builder()
                        .post(post)
                        .departureRegion("서울")
                        .meetingPlace("서울역")
                        .departureLatitude(BigDecimal.valueOf(37.5))
                        .departureLongitude(BigDecimal.valueOf(127.0))
                        .destinationResort(resort)
                        .tripType(CarpoolTripType.ONE_WAY)
                        .departureAt(now.plusDays(1))
                        .passengerCapacity(2)
                        .fuelType(CarpoolFuelType.GASOLINE)
                        .fuelEfficiency(BigDecimal.valueOf(12))
                        .costMode(CarpoolCostMode.AUTO)
                        .fuelPrice(BigDecimal.valueOf(1700))
                        .fuelPriceObservedAt(now)
                        .routeDistanceKm(BigDecimal.valueOf(100))
                        .routeTollFee(5000)
                        .estimatedFuelCost(15000)
                        .estimatedTotalCost(20000)
                        .estimatedCostPerPerson(6667)
                        .routeSource(CarpoolRouteSource.KAKAO)
                        .routeCalculatedAt(now)
                        .contactPublicToGuest(false)
                        .equipmentLoadAvailable(true)
                        .build();

        CarpoolResponse response = CarpoolResponse.from(post, detail, null, false);

        assertThat(response.equipmentLoadAvailable()).isTrue();
        assertThat(response.departureLatitude()).isNull();
        assertThat(response.departureLongitude()).isNull();

        CarpoolResponse managerResponse = CarpoolResponse.from(post, detail, null, true);
        assertThat(managerResponse.departureLatitude()).isEqualByComparingTo("37.5");
        assertThat(managerResponse.departureLongitude()).isEqualByComparingTo("127.0");
    }
}
