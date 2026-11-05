package com.ikae.snowthing.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolCreateRequest;
import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.carpool.repository.CarpoolDetailRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.search.service.SearchOutboxWriter;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class CarpoolWriterServiceTest {

    private static final String OWNER_PUBLIC_ID = "owner-public-id";
    private static final String OTHER_PUBLIC_ID = "other-public-id";

    private final PostRepository postRepository = mock(PostRepository.class);
    private final CarpoolDetailRepository carpoolDetailRepository =
            mock(CarpoolDetailRepository.class);
    private final ResortRepository resortRepository = mock(ResortRepository.class);
    private CarpoolWriterService service;
    private Post post;

    @BeforeEach
    void setUp() {
        service =
                new CarpoolWriterService(
                        postRepository,
                        mock(PostCategoryRepository.class),
                        mock(MemberRepository.class),
                        resortRepository,
                        carpoolDetailRepository,
                        mock(SearchOutboxWriter.class));
        Member owner =
                Member.builder()
                        .publicId(OWNER_PUBLIC_ID)
                        .email("owner@example.com")
                        .nickname("owner")
                        .build();
        PostCategory category = PostCategory.builder().name("카풀").code("CARPOOL").build();
        post =
                Post.builder()
                        .member(owner)
                        .category(category)
                        .title("title")
                        .content("content")
                        .writerIp("127.0.0.1")
                        .build();
        when(postRepository.findWithMemberAndCategoryByPublicId(post.getPublicId()))
                .thenReturn(Optional.of(post));
        when(carpoolDetailRepository.findByPostPublicId(post.getPublicId()))
                .thenReturn(Optional.of(mock(CarpoolDetail.class)));
    }

    @Test
    void ownerCanSoftDeleteCarpoolPost() {
        service.delete(post.getPublicId(), OWNER_PUBLIC_ID, false);

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    void nonOwnerCannotDeleteCarpoolPost() {
        assertThatThrownBy(() -> service.delete(post.getPublicId(), OTHER_PUBLIC_ID, false))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    void nonOwnerCannotUpdateCarpoolPost() {
        assertThatThrownBy(
                        () ->
                                service.update(
                                        post.getPublicId(), null, OTHER_PUBLIC_ID, false, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void updateToManualCostClearsCalculationValues() {
        CarpoolDetail detail =
                CarpoolDetail.builder()
                        .post(post)
                        .departureLatitude(BigDecimal.valueOf(37.5))
                        .departureLongitude(BigDecimal.valueOf(127.0))
                        .fuelType(CarpoolFuelType.GASOLINE)
                        .fuelPrice(BigDecimal.valueOf(1700))
                        .routeDistanceKm(BigDecimal.valueOf(100))
                        .routeTollFee(5000)
                        .estimatedTotalCost(20000)
                        .estimatedCostPerPerson(6667)
                        .costMode(CarpoolCostMode.AUTO)
                        .routeSource(CarpoolRouteSource.KAKAO)
                        .build();
        Resort resort = mock(Resort.class);
        when(resort.isActive()).thenReturn(true);
        when(resortRepository.findById(1L)).thenReturn(Optional.of(resort));
        when(carpoolDetailRepository.findByPostPublicId(post.getPublicId()))
                .thenReturn(Optional.of(detail));

        service.update(post.getPublicId(), createRequest(), OWNER_PUBLIC_ID, false, null);

        assertThat(detail.getCostMode()).isEqualTo(CarpoolCostMode.MANUAL);
        assertThat(detail.getEstimatedCostPerPerson()).isEqualTo(10000);
        assertThat(detail.getEstimatedTotalCost()).isNull();
        assertThat(detail.getRouteDistanceKm()).isNull();
        assertThat(detail.getRouteTollFee()).isNull();
        assertThat(detail.getFuelPrice()).isNull();
        assertThat(detail.getFuelType()).isNull();
        assertThat(detail.getRouteSource()).isNull();
        assertThat(detail.getDepartureLatitude()).isNull();
    }

    @Test
    void autoCostStoresCalculatedTotalLikePreview() {
        CarpoolDetail detail =
                CarpoolDetail.builder().post(post).costMode(CarpoolCostMode.MANUAL).build();
        Resort resort = mock(Resort.class);
        when(resort.isActive()).thenReturn(true);
        when(resortRepository.findById(1L)).thenReturn(Optional.of(resort));
        when(carpoolDetailRepository.findByPostPublicId(post.getPublicId()))
                .thenReturn(Optional.of(detail));
        CarpoolCreateRequest request =
                new CarpoolCreateRequest(
                        "title",
                        "content",
                        "서울",
                        "서울역",
                        BigDecimal.valueOf(37.5),
                        BigDecimal.valueOf(127.0),
                        1L,
                        CarpoolTripType.ONE_WAY,
                        LocalDateTime.now().plusDays(1),
                        null,
                        2,
                        CarpoolFuelType.GASOLINE,
                        BigDecimal.valueOf(12),
                        CarpoolCostMode.AUTO,
                        null,
                        true,
                        null,
                        false);
        CarpoolAutoPreviewResponse calculation =
                new CarpoolAutoPreviewResponse(
                        BigDecimal.valueOf(100),
                        5000,
                        3600,
                        BigDecimal.valueOf(1700),
                        null,
                        LocalDateTime.now(),
                        15001,
                        20001,
                        6667,
                        3,
                        CarpoolRouteSource.KAKAO,
                        LocalDateTime.now());

        service.update(post.getPublicId(), request, OWNER_PUBLIC_ID, false, calculation);

        assertThat(detail.getEstimatedTotalCost()).isEqualTo(20001);
        assertThat(detail.getEstimatedCostPerPerson()).isEqualTo(6667);
    }

    private CarpoolCreateRequest createRequest() {
        return new CarpoolCreateRequest(
                "title",
                "content",
                "서울 강동구",
                "천호역 1번 출구",
                null,
                null,
                1L,
                CarpoolTripType.ONE_WAY,
                LocalDateTime.now().plusDays(1),
                null,
                3,
                null,
                null,
                CarpoolCostMode.MANUAL,
                BigDecimal.valueOf(10000),
                true,
                null,
                false,
                null,
                null,
                null,
                null);
    }

    @Test
    void adminCanSoftDeleteOtherMembersCarpoolPost() {
        service.delete(post.getPublicId(), OTHER_PUBLIC_ID, true);

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
    }
}
