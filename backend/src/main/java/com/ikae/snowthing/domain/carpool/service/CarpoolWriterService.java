package com.ikae.snowthing.domain.carpool.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolCreateRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolResponse;
import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelPriceSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;
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
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CarpoolWriterService {

    private static final String CARPOOL_CATEGORY_CODE = "CARPOOL";
    private static final String DEFAULT_WRITER_IP = "127.0.0.1";

    private final PostRepository postRepository;
    private final PostCategoryRepository categoryRepository;
    private final MemberRepository memberRepository;
    private final ResortRepository resortRepository;
    private final CarpoolDetailRepository carpoolDetailRepository;
    private final com.ikae.snowthing.domain.search.service.SearchOutboxWriter searchOutbox;
    private final com.ikae.snowthing.domain.image.service.PostImageLifecycleService imageLifecycle;

    @Transactional
    public CarpoolResponse save(
            CarpoolCreateRequest request,
            String memberPublicId,
            String clientIp,
            CarpoolAutoPreviewResponse calculation) {
        Member member =
                memberRepository
                        .findByPublicId(memberPublicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        PostCategory category =
                categoryRepository
                        .findByCode(CARPOOL_CATEGORY_CODE)
                        .orElseThrow(() -> new CustomException(ErrorCode.POST_CATEGORY_NOT_FOUND));
        Resort resort =
                resortRepository
                        .findById(request.destinationResortId())
                        .filter(Resort::isActive)
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CARPOOL_VALUE));

        Post post =
                Post.builder()
                        .member(member)
                        .category(category)
                        .title(request.title())
                        .content(request.content())
                        .writerIp(resolveWriterIp(clientIp))
                        .isAnonymous(false)
                        .hasImage(false)
                        .build();
        postRepository.save(post);
        searchOutbox.postChanged(post);

        CostSnapshot cost = CostSnapshot.of(request, calculation);
        CarpoolDetail detail =
                CarpoolDetail.builder()
                        .post(post)
                        .departureRegion(request.departureRegion())
                        .meetingPlace(request.meetingPlace())
                        .departureLatitude(cost.departureLatitude())
                        .departureLongitude(cost.departureLongitude())
                        .destinationResort(resort)
                        .destinationLatitude(resort.getRouteLatitude())
                        .destinationLongitude(resort.getRouteLongitude())
                        .tripType(request.tripType())
                        .departureAt(request.departureAt())
                        .returnAt(request.returnAt())
                        .passengerCapacity(request.passengerCapacity())
                        .fuelType(cost.fuelType())
                        .fuelEfficiency(cost.fuelEfficiency())
                        .costMode(request.costMode())
                        .fuelPrice(cost.fuelPrice())
                        .fuelPriceSource(cost.fuelPriceSource())
                        .fuelPriceObservedAt(cost.fuelPriceObservedAt())
                        .routeDistanceKm(cost.routeDistanceKm())
                        .routeTollFee(cost.routeTollFee())
                        .estimatedFuelCost(cost.estimatedFuelCost())
                        .estimatedTotalCost(cost.estimatedTotalCost())
                        .estimatedCostPerPerson(cost.estimatedCostPerPerson())
                        .routeSource(cost.routeSource())
                        .routeCalculatedAt(cost.routeCalculatedAt())
                        .contactInfo(request.contactInfo())
                        .contactPublicToGuest(request.contactPublicToGuest())
                        .equipmentLoadAvailable(request.equipmentLoadAvailable())
                        .build();
        carpoolDetailRepository.save(detail);
        return CarpoolResponse.from(post, detail, detail.getContactInfo(), true);
    }

    @Transactional
    public CarpoolResponse update(
            String publicId,
            CarpoolCreateRequest request,
            String memberPublicId,
            boolean admin,
            CarpoolAutoPreviewResponse calculation) {
        Post post =
                postRepository
                        .findWithMemberAndCategoryByPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        validateActivePost(post);
        validateManagePermission(post, memberPublicId, admin);
        CarpoolDetail detail =
                carpoolDetailRepository
                        .findByPostPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CARPOOL_DETAIL_REQUIRED));
        Resort resort =
                resortRepository
                        .findById(request.destinationResortId())
                        .filter(Resort::isActive)
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CARPOOL_VALUE));

        post.update(request.title(), request.content(), post.getCategory());
        searchOutbox.postChanged(post);
        CostSnapshot cost = CostSnapshot.of(request, calculation);
        detail.update(
                request.departureRegion(),
                request.meetingPlace(),
                cost.departureLatitude(),
                cost.departureLongitude(),
                resort,
                resort.getRouteLatitude(),
                resort.getRouteLongitude(),
                request.tripType(),
                request.departureAt(),
                request.returnAt(),
                request.passengerCapacity(),
                cost.fuelType(),
                cost.fuelEfficiency(),
                request.costMode(),
                cost.fuelPrice(),
                cost.fuelPriceSource(),
                cost.fuelPriceObservedAt(),
                cost.routeDistanceKm(),
                cost.routeTollFee(),
                cost.estimatedFuelCost(),
                cost.estimatedTotalCost(),
                cost.estimatedCostPerPerson(),
                cost.routeSource(),
                cost.routeCalculatedAt(),
                request.contactInfo(),
                request.contactPublicToGuest(),
                request.equipmentLoadAvailable());
        return CarpoolResponse.from(post, detail, detail.getContactInfo(), true);
    }

    @Transactional
    public void delete(String publicId, String memberPublicId, boolean admin) {
        Post post =
                postRepository
                        .findWithMemberAndCategoryByPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        validateActivePost(post);
        validateManagePermission(post, memberPublicId, admin);
        if (carpoolDetailRepository.findByPostPublicId(publicId).isEmpty()) {
            throw new CustomException(ErrorCode.CARPOOL_DETAIL_REQUIRED);
        }
        post.softDelete();
        imageLifecycle.releasePostImages(post);
        searchOutbox.postChanged(post);
    }

    private void validateManagePermission(Post post, String memberPublicId, boolean admin) {
        boolean owner =
                post.getMember() != null && post.getMember().getPublicId().equals(memberPublicId);
        if (!owner && !admin) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void validateActivePost(Post post) {
        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private String resolveWriterIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? DEFAULT_WRITER_IP : clientIp;
    }

    /**
     * 저장할 비용 값. 직접 입력(MANUAL)은 계산하지 않으므로 1인 금액 외에는 모두 null이다.
     *
     * <p>출발 좌표는 계산 값이 아니라 지도에서 고른 출발 장소라 직접 입력에서도 둘 다 있으면 저장한다.
     */
    private record CostSnapshot(
            BigDecimal departureLatitude,
            BigDecimal departureLongitude,
            CarpoolFuelType fuelType,
            BigDecimal fuelEfficiency,
            BigDecimal fuelPrice,
            CarpoolFuelPriceSource fuelPriceSource,
            LocalDateTime fuelPriceObservedAt,
            BigDecimal routeDistanceKm,
            Integer routeTollFee,
            Integer estimatedFuelCost,
            Integer estimatedTotalCost,
            int estimatedCostPerPerson,
            CarpoolRouteSource routeSource,
            LocalDateTime routeCalculatedAt) {

        static CostSnapshot of(
                CarpoolCreateRequest request, CarpoolAutoPreviewResponse calculation) {
            if (request.costMode() == CarpoolCostMode.MANUAL) {
                boolean hasCoordinate =
                        request.departureLatitude() != null && request.departureLongitude() != null;
                return new CostSnapshot(
                        hasCoordinate ? request.departureLatitude() : null,
                        hasCoordinate ? request.departureLongitude() : null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        request.manualCostPerPerson().intValueExact(),
                        null,
                        null);
            }
            return new CostSnapshot(
                    request.departureLatitude(),
                    request.departureLongitude(),
                    request.fuelType(),
                    request.fuelEfficiency(),
                    calculation.fuelPrice(),
                    calculation.fuelPriceSource(),
                    calculation.fuelPriceObservedAt(),
                    calculation.distanceKm(),
                    calculation.tollFee(),
                    calculation.estimatedFuelCost(),
                    calculation.estimatedTotalCost(),
                    calculation.estimatedCostPerPerson(),
                    calculation.routeSource(),
                    calculation.routeCalculatedAt());
        }
    }
}
