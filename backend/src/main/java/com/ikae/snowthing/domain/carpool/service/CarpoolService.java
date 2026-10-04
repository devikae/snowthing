package com.ikae.snowthing.domain.carpool.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolCreateRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolSummaryResponse;
import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.carpool.repository.CarpoolDetailRepository;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CarpoolService {

    private static final int MAX_PAGE_INDEX = 99;
    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final CarpoolDetailRepository carpoolDetailRepository;
    private final CarpoolAutoCalculationService carpoolAutoCalculationService;
    private final CarpoolWriterService carpoolWriterService;

    public CarpoolResponse create(
            CarpoolCreateRequest request, CustomUserDetails userDetails, String clientIp) {
        requireAuthenticated(userDetails);
        validateRequest(request);
        CarpoolAutoPreviewResponse calculation = calculate(request);
        return carpoolWriterService.save(request, userDetails.getPublicId(), clientIp, calculation);
    }

    public CarpoolResponse update(
            String publicId, CarpoolCreateRequest request, CustomUserDetails userDetails) {
        requireAuthenticated(userDetails);
        validateRequest(request);
        validateManagePermission(publicId, userDetails);
        CarpoolAutoPreviewResponse calculation = calculate(request);
        return carpoolWriterService.update(
                publicId, request, userDetails.getPublicId(), isAdmin(userDetails), calculation);
    }

    public void delete(String publicId, CustomUserDetails userDetails) {
        requireAuthenticated(userDetails);
        carpoolWriterService.delete(publicId, userDetails.getPublicId(), isAdmin(userDetails));
    }

    @Transactional(readOnly = true)
    public CarpoolResponse find(String publicId, CustomUserDetails userDetails) {
        Post post =
                postRepository
                        .findWithMemberAndCategoryByPublicId(publicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
        CarpoolDetail detail =
                carpoolDetailRepository
                        .findByPostPublicId(publicId)
                        .orElseThrow(
                                () -> new CustomAuthException(ErrorCode.CARPOOL_DETAIL_REQUIRED));
        boolean canManage = isManager(post, userDetails);
        String contactInfo =
                detail.isContactPublicToGuest() || canManage ? detail.getContactInfo() : null;
        return CarpoolResponse.from(post, detail, contactInfo, canManage);
    }

    @Transactional(readOnly = true)
    public Page<CarpoolSummaryResponse> findPage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }
        if (page > MAX_PAGE_INDEX) {
            throw new CustomAuthException(ErrorCode.CARPOOL_PAGE_LIMIT_EXCEEDED);
        }
        Page<CarpoolDetail> details =
                carpoolDetailRepository
                        .findByPostStatusAndPostIsDeletedFalseAndDepartureAtGreaterThanEqual(
                                PostStatus.NORMAL,
                                LocalDateTime.now(),
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by(Sort.Direction.DESC, "createdAt")
                                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        return details.map(CarpoolSummaryResponse::from);
    }

    private CarpoolAutoPreviewResponse calculate(CarpoolCreateRequest request) {
        return carpoolAutoCalculationService.preview(
                new CarpoolAutoPreviewRequest(
                        request.departureLongitude(),
                        request.departureLatitude(),
                        request.destinationResortId(),
                        request.tripType(),
                        request.fuelType(),
                        request.fuelEfficiency(),
                        request.passengerCapacity(),
                        request.routeSource(),
                        request.manualDistanceKm(),
                        request.manualTollFee(),
                        request.manualFuelPrice()));
    }

    private void validateRequest(CarpoolCreateRequest request) {
        validateSchedule(request);
        if (request.costMode() == CarpoolCostMode.MANUAL && request.manualCostPerPerson() == null) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
        if (request.routeSource() == null
                || (request.routeSource() == CarpoolRouteSource.KAKAO
                        && (request.departureLatitude() == null
                                || request.departureLongitude() == null))) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
    }

    private void requireAuthenticated(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    private boolean isManager(Post post, CustomUserDetails userDetails) {
        return userDetails != null
                && (isAdmin(userDetails)
                        || (post.getMember() != null
                                && post.getMember()
                                        .getPublicId()
                                        .equals(userDetails.getPublicId())));
    }

    private void validateManagePermission(String publicId, CustomUserDetails userDetails) {
        Post post =
                postRepository
                        .findWithMemberAndCategoryByPublicId(publicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
        if (!isManager(post, userDetails)) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Role.ROLE_ADMIN.getKey()));
    }

    private void validateSchedule(CarpoolCreateRequest request) {
        if (request.tripType() == CarpoolTripType.ONE_WAY && request.returnAt() != null) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_SCHEDULE);
        }
        if (request.tripType() == CarpoolTripType.ROUND_TRIP
                && (request.returnAt() == null
                        || !request.returnAt().isAfter(request.departureAt()))) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_SCHEDULE);
        }
    }
}
