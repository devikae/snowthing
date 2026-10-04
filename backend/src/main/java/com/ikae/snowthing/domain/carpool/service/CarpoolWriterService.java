package com.ikae.snowthing.domain.carpool.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolCreateRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolResponse;
import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
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

        CarpoolDetail detail =
                CarpoolDetail.builder()
                        .post(post)
                        .departureRegion(request.departureRegion())
                        .meetingPlace(request.meetingPlace())
                        .departureLatitude(request.departureLatitude())
                        .departureLongitude(request.departureLongitude())
                        .destinationResort(resort)
                        .destinationLatitude(resort.getRouteLatitude())
                        .destinationLongitude(resort.getRouteLongitude())
                        .tripType(request.tripType())
                        .departureAt(request.departureAt())
                        .returnAt(request.returnAt())
                        .passengerCapacity(request.passengerCapacity())
                        .fuelType(request.fuelType())
                        .fuelEfficiency(request.fuelEfficiency())
                        .costMode(request.costMode())
                        .fuelPrice(calculation.fuelPrice())
                        .fuelPriceSource(calculation.fuelPriceSource())
                        .fuelPriceObservedAt(calculation.fuelPriceObservedAt())
                        .routeDistanceKm(calculation.distanceKm())
                        .routeTollFee(calculation.tollFee())
                        .estimatedFuelCost(calculation.estimatedFuelCost())
                        .estimatedTotalCost(resolveTotalCost(request, calculation))
                        .estimatedCostPerPerson(resolveCostPerPerson(request, calculation))
                        .routeSource(calculation.routeSource())
                        .routeCalculatedAt(calculation.routeCalculatedAt())
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
        detail.update(
                request.departureRegion(),
                request.meetingPlace(),
                request.departureLatitude(),
                request.departureLongitude(),
                resort,
                resort.getRouteLatitude(),
                resort.getRouteLongitude(),
                request.tripType(),
                request.departureAt(),
                request.returnAt(),
                request.passengerCapacity(),
                request.fuelType(),
                request.fuelEfficiency(),
                request.costMode(),
                calculation.fuelPrice(),
                calculation.fuelPriceSource(),
                calculation.fuelPriceObservedAt(),
                calculation.distanceKm(),
                calculation.tollFee(),
                calculation.estimatedFuelCost(),
                resolveTotalCost(request, calculation),
                resolveCostPerPerson(request, calculation),
                calculation.routeSource(),
                calculation.routeCalculatedAt(),
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

    private int resolveCostPerPerson(
            CarpoolCreateRequest request, CarpoolAutoPreviewResponse calculation) {
        return request.costMode() == CarpoolCostMode.MANUAL
                ? request.manualCostPerPerson().intValueExact()
                : calculation.estimatedCostPerPerson();
    }

    private int resolveTotalCost(
            CarpoolCreateRequest request, CarpoolAutoPreviewResponse calculation) {
        return Math.multiplyExact(
                resolveCostPerPerson(request, calculation), calculation.totalPassengerCount());
    }
}
