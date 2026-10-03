package com.ikae.snowthing.domain.carpool.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.carpool.dto.CarpoolCreateRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolResponse;
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
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CarpoolService {

    private static final String CARPOOL_CATEGORY_CODE = "CARPOOL";
    private static final String DEFAULT_WRITER_IP = "127.0.0.1";
    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final PostCategoryRepository categoryRepository;
    private final MemberRepository memberRepository;
    private final ResortRepository resortRepository;
    private final CarpoolDetailRepository carpoolDetailRepository;

    @Transactional
    public CarpoolResponse create(
            CarpoolCreateRequest request, CustomUserDetails userDetails, String clientIp) {
        Member member = findMember(userDetails);
        validateSchedule(request);
        PostCategory category = findCarpoolCategory();
        Resort resort =
                resortRepository
                        .findById(request.destinationResortId())
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.INVALID_INPUT));

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

        CarpoolCostCalculator.Calculation calculation =
                CarpoolCostCalculator.calculate(
                        request.routeDistanceKm(),
                        request.fuelEfficiency(),
                        request.fuelPrice(),
                        request.routeTollFee(),
                        request.passengerCapacity());
        CarpoolDetail detail =
                CarpoolDetail.builder()
                        .post(post)
                        .departureRegion(request.departureRegion())
                        .meetingPlace(request.meetingPlace())
                        .departureLatitude(request.departureLatitude())
                        .departureLongitude(request.departureLongitude())
                        .destinationResort(resort)
                        .tripType(request.tripType())
                        .departureAt(request.departureAt())
                        .returnAt(request.returnAt())
                        .passengerCapacity(request.passengerCapacity())
                        .fuelType(request.fuelType())
                        .fuelEfficiency(request.fuelEfficiency())
                        .fuelPrice(request.fuelPrice())
                        .fuelPriceObservedAt(LocalDateTime.now())
                        .routeDistanceKm(request.routeDistanceKm())
                        .routeTollFee(request.routeTollFee())
                        .estimatedFuelCost(calculation.estimatedFuelCost())
                        .estimatedTotalCost(calculation.estimatedTotalCost())
                        .estimatedCostPerPerson(calculation.estimatedCostPerPerson())
                        .routeSource(request.routeSource())
                        .routeCalculatedAt(LocalDateTime.now())
                        .contactInfo(request.contactInfo())
                        .contactPublicToGuest(request.contactPublicToGuest())
                        .build();
        carpoolDetailRepository.save(detail);
        return CarpoolResponse.from(post, detail, detail.getContactInfo());
    }

    @Transactional(readOnly = true)
    public CarpoolResponse find(String publicId, CustomUserDetails userDetails) {
        Post post =
                postRepository
                        .findWithMemberAndCategoryByPublicId(publicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
        CarpoolDetail detail =
                carpoolDetailRepository
                        .findByPostPublicId(publicId)
                        .orElseThrow(
                                () -> new CustomAuthException(ErrorCode.CARPOOL_DETAIL_REQUIRED));
        boolean isOwner =
                userDetails != null
                        && post.getMember() != null
                        && post.getMember().getPublicId().equals(userDetails.getPublicId());
        String contactInfo =
                detail.isContactPublicToGuest() || isOwner ? detail.getContactInfo() : null;
        return CarpoolResponse.from(post, detail, contactInfo);
    }

    @Transactional(readOnly = true)
    public Page<CarpoolResponse> findPage(int page, int size, CustomUserDetails userDetails) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }
        Page<CarpoolDetail> details =
                carpoolDetailRepository.findByPostStatusAndPostIsDeletedFalse(
                        PostStatus.NORMAL,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Direction.DESC, "createdAt")
                                        .and(Sort.by(Sort.Direction.DESC, "id"))));
        return details.map(
                detail -> {
                    boolean isOwner =
                            userDetails != null
                                    && detail.getPost().getMember() != null
                                    && detail.getPost()
                                            .getMember()
                                            .getPublicId()
                                            .equals(userDetails.getPublicId());
                    String contactInfo =
                            detail.isContactPublicToGuest() || isOwner
                                    ? detail.getContactInfo()
                                    : null;
                    return CarpoolResponse.from(detail.getPost(), detail, contactInfo);
                });
    }

    private Member findMember(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
        }
        return memberRepository
                .findByPublicId(userDetails.getPublicId())
                .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private PostCategory findCarpoolCategory() {
        return categoryRepository
                .findByCode(CARPOOL_CATEGORY_CODE)
                .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_CATEGORY_NOT_FOUND));
    }

    private void validateSchedule(CarpoolCreateRequest request) {
        if (request.tripType().name().equals("ONE_WAY") && request.returnAt() != null) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_SCHEDULE);
        }
        if (request.tripType().name().equals("ROUND_TRIP")
                && (request.returnAt() == null
                        || !request.returnAt().isAfter(request.departureAt()))) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_SCHEDULE);
        }
    }

    private String resolveWriterIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? DEFAULT_WRITER_IP : clientIp;
    }
}
