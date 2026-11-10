package com.ikae.snowthing.domain.carpool.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.carpool.dto.CarpoolMyReviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolParticipationResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolParticipationResponse.Participant;
import com.ikae.snowthing.domain.carpool.dto.CarpoolReviewCreateRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolReviewSummaryResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolReviewSummaryResponse.TagCount;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReview;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewRating;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewTag;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRider;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRiderStatus;
import com.ikae.snowthing.domain.carpool.repository.CarpoolDetailRepository;
import com.ikae.snowthing.domain.carpool.repository.CarpoolReviewRepository;
import com.ikae.snowthing.domain.carpool.repository.CarpoolRiderRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 카풀 탑승 확인과 후기. 같이 탄 사람 = 글 작성자(운전자) + CONFIRMED 탑승자.
 *
 * <p>설계: docs/conception/027_carpool_review/01_design.md
 */
@Service
public class CarpoolReviewService {

    static final int OPEN_DAYS = 30;
    static final int TOP_TAG_LIMIT = 3;
    static final int RECEIVED_LIMIT = 20;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PostRepository postRepository;
    private final CarpoolDetailRepository carpoolDetailRepository;
    private final CarpoolRiderRepository riderRepository;
    private final CarpoolReviewRepository reviewRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Autowired
    public CarpoolReviewService(
            PostRepository postRepository,
            CarpoolDetailRepository carpoolDetailRepository,
            CarpoolRiderRepository riderRepository,
            CarpoolReviewRepository reviewRepository,
            MemberRepository memberRepository) {
        this(
                postRepository,
                carpoolDetailRepository,
                riderRepository,
                reviewRepository,
                memberRepository,
                Clock.system(KST));
    }

    CarpoolReviewService(
            PostRepository postRepository,
            CarpoolDetailRepository carpoolDetailRepository,
            CarpoolRiderRepository riderRepository,
            CarpoolReviewRepository reviewRepository,
            MemberRepository memberRepository,
            Clock clock) {
        this.postRepository = postRepository;
        this.carpoolDetailRepository = carpoolDetailRepository;
        this.riderRepository = riderRepository;
        this.reviewRepository = reviewRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CarpoolParticipationResponse participation(
            String publicId, CustomUserDetails userDetails) {
        Post post = visiblePost(publicId);
        CarpoolDetail detail = detailOf(post);
        Member driver = post.getMember();
        Long viewerId = userDetails == null ? null : userDetails.getMember().getId();
        List<CarpoolRider> riders = riderRepository.findAllWithMember(detail.getId());

        String myStatus = null;
        if (viewerId != null && isDriver(post, viewerId)) {
            myStatus = CarpoolParticipationResponse.DRIVER;
        } else if (viewerId != null) {
            myStatus =
                    riders.stream()
                            .filter(rider -> rider.getMember().getId().equals(viewerId))
                            .map(rider -> rider.getStatus().name())
                            .findFirst()
                            .orElse(null);
        }
        boolean viewerIsDriver = CarpoolParticipationResponse.DRIVER.equals(myStatus);
        boolean viewerIsParticipant =
                viewerIsDriver || CarpoolRiderStatus.CONFIRMED.name().equals(myStatus);

        List<Participant> members = new ArrayList<>();
        if (viewerIsParticipant) {
            Set<Long> reviewed =
                    new HashSet<>(reviewRepository.findRevieweeIds(detail.getId(), viewerId));
            if (driver != null) {
                members.add(
                        Participant.of(
                                driver,
                                CarpoolParticipationResponse.DRIVER,
                                CarpoolRiderStatus.CONFIRMED,
                                reviewed.contains(driver.getId())));
            }
            for (CarpoolRider rider : riders) {
                boolean visible =
                        rider.getStatus() == CarpoolRiderStatus.CONFIRMED
                                || (viewerIsDriver && rider.isRequested());
                if (visible) {
                    members.add(
                            Participant.of(
                                    rider.getMember(),
                                    CarpoolParticipationResponse.RIDER,
                                    rider.getStatus(),
                                    reviewed.contains(rider.getMember().getId())));
                }
            }
        }
        long confirmedCount =
                riders.stream().filter(r -> r.getStatus() == CarpoolRiderStatus.CONFIRMED).count();
        LocalDateTime now = now();
        boolean departed = !now.isBefore(detail.getDepartureAt());
        return new CarpoolParticipationResponse(
                myStatus,
                departed,
                departed && !now.isAfter(closesAt(detail)),
                confirmedCount,
                detail.getPassengerCapacity(),
                members);
    }

    /** 출발 뒤 탑승자가 "이 카풀 탔어요"를 누른다. 운전자가 확정해야 같이 탄 사람이 된다. */
    @Transactional
    public void requestRide(String publicId, CustomUserDetails userDetails) {
        Long memberId = requireWriter(userDetails);
        Post post = visiblePost(publicId);
        CarpoolDetail detail = detailOf(post);
        requireOpen(detail);
        if (isDriver(post, memberId)) {
            throw new CustomException(ErrorCode.CARPOOL_RIDE_DRIVER_NOT_ALLOWED);
        }
        if (riderRepository.existsByCarpool_IdAndMember_Id(detail.getId(), memberId)) {
            throw new CustomException(ErrorCode.CARPOOL_RIDE_ALREADY_REQUESTED);
        }
        try {
            riderRepository.saveAndFlush(
                    new CarpoolRider(detail, memberRepository.getReferenceById(memberId)));
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.CARPOOL_RIDE_ALREADY_REQUESTED, exception);
        }
    }

    /** 운전자만 결정한다. 결정은 한 번이고, 확정은 모집 인원을 넘지 않는다. */
    @Transactional
    public void decideRide(
            String publicId, String riderPublicId, boolean confirm, CustomUserDetails userDetails) {
        Long memberId = requireWriter(userDetails);
        Post post = visiblePost(publicId);
        if (!isDriver(post, memberId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        // 동시 결정을 줄 세우려고 카풀 행을 먼저 잠근다. 그 뒤의 탑승 행 읽기도 잠금 읽기라서
        // 앞서 커밋된 확정과 거절을 본다(일반 SELECT는 트랜잭션 첫 읽기 시점 스냅샷을 본다).
        CarpoolDetail detail =
                carpoolDetailRepository
                        .findByIdForUpdate(post.getId())
                        .orElseThrow(() -> new CustomException(ErrorCode.CARPOOL_DETAIL_REQUIRED));
        Member rider =
                memberRepository
                        .findByPublicId(riderPublicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CARPOOL_RIDE_NOT_FOUND));
        CarpoolRider row =
                riderRepository
                        .findForUpdate(detail.getId(), rider.getId())
                        .orElseThrow(() -> new CustomException(ErrorCode.CARPOOL_RIDE_NOT_FOUND));
        if (!row.isRequested()) {
            throw new CustomException(ErrorCode.CARPOOL_RIDE_ALREADY_DECIDED);
        }
        if (!confirm) {
            row.reject();
            return;
        }
        long confirmed =
                riderRepository
                        .findForUpdateByStatus(detail.getId(), CarpoolRiderStatus.CONFIRMED)
                        .size();
        if (confirmed >= detail.getPassengerCapacity()) {
            throw new CustomException(ErrorCode.CARPOOL_RIDE_CAPACITY_FULL);
        }
        row.confirm();
    }

    @Transactional
    public void writeReview(
            String publicId, CarpoolReviewCreateRequest request, CustomUserDetails userDetails) {
        Long reviewerId = requireWriter(userDetails);
        Post post = visiblePost(publicId);
        CarpoolDetail detail = detailOf(post);
        requireOpen(detail);
        Member reviewee =
                memberRepository
                        .findByPublicId(request.revieweePublicId())
                        .filter(member -> !member.isWithdrawn())
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        if (reviewee.getId().equals(reviewerId)) {
            throw new CustomException(ErrorCode.CARPOOL_REVIEW_SELF_NOT_ALLOWED);
        }
        if (!isParticipant(post, detail, reviewerId)
                || !isParticipant(post, detail, reviewee.getId())) {
            throw new CustomException(ErrorCode.CARPOOL_REVIEW_NOT_PARTICIPANT);
        }
        Set<CarpoolReviewTag> tags = validTags(request, isDriver(post, reviewee.getId()));
        if (reviewRepository.existsByCarpool_IdAndReviewer_IdAndReviewee_Id(
                detail.getId(), reviewerId, reviewee.getId())) {
            throw new CustomException(ErrorCode.CARPOOL_REVIEW_DUPLICATED);
        }
        String comment =
                request.comment() == null || request.comment().isBlank()
                        ? null
                        : request.comment().trim();
        try {
            reviewRepository.saveAndFlush(
                    new CarpoolReview(
                            detail,
                            memberRepository.getReferenceById(reviewerId),
                            reviewee,
                            request.rating(),
                            tags,
                            comment));
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.CARPOOL_REVIEW_DUPLICATED, exception);
        }
    }

    @Transactional(readOnly = true)
    public CarpoolReviewSummaryResponse summary(String memberPublicId) {
        Member member =
                memberRepository
                        .findByPublicId(memberPublicId)
                        .filter(found -> !found.isWithdrawn())
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        return new CarpoolReviewSummaryResponse(
                rideCount(member.getId()),
                reviewRepository.countReceived(member.getId(), CarpoolReviewRating.GOOD),
                topTags(member.getId()));
    }

    @Transactional(readOnly = true)
    public CarpoolMyReviewResponse mine(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.LOGIN_REQUIRED);
        }
        Long memberId = userDetails.getMember().getId();
        return new CarpoolMyReviewResponse(
                rideCount(memberId),
                reviewRepository.countReceived(memberId, null),
                reviewRepository.countReceived(memberId, CarpoolReviewRating.GOOD),
                topTags(memberId),
                reviewRepository.findReceived(memberId, PageRequest.of(0, RECEIVED_LIMIT)).stream()
                        .map(CarpoolMyReviewResponse.Received::from)
                        .toList());
    }

    private long rideCount(Long memberId) {
        return riderRepository.countConfirmedRides(memberId)
                + riderRepository.countDrivenRides(memberId);
    }

    private List<TagCount> topTags(Long memberId) {
        return reviewRepository.countGoodTags(memberId, PageRequest.of(0, TOP_TAG_LIMIT)).stream()
                .map(row -> new TagCount((CarpoolReviewTag) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    private Set<CarpoolReviewTag> validTags(
            CarpoolReviewCreateRequest request, boolean revieweeIsDriver) {
        List<CarpoolReviewTag> requested = request.tags() == null ? List.of() : request.tags();
        if (requested.isEmpty()) {
            return EnumSet.noneOf(CarpoolReviewTag.class);
        }
        boolean invalid =
                request.rating() != CarpoolReviewRating.GOOD
                        || requested.stream().anyMatch(Objects::isNull)
                        || (!revieweeIsDriver
                                && requested.stream().anyMatch(CarpoolReviewTag::isDriverOnly));
        if (invalid) {
            throw new CustomException(ErrorCode.CARPOOL_REVIEW_INVALID_TAG);
        }
        return EnumSet.copyOf(requested);
    }

    private boolean isParticipant(Post post, CarpoolDetail detail, Long memberId) {
        return isDriver(post, memberId)
                || riderRepository
                        .findByCarpool_IdAndMember_Id(detail.getId(), memberId)
                        .filter(rider -> rider.getStatus() == CarpoolRiderStatus.CONFIRMED)
                        .isPresent();
    }

    private boolean isDriver(Post post, Long memberId) {
        return post.getMember() != null && post.getMember().getId().equals(memberId);
    }

    private void requireOpen(CarpoolDetail detail) {
        LocalDateTime now = now();
        if (now.isBefore(detail.getDepartureAt())) {
            throw new CustomException(ErrorCode.CARPOOL_NOT_DEPARTED);
        }
        if (now.isAfter(closesAt(detail))) {
            throw new CustomException(ErrorCode.CARPOOL_REVIEW_PERIOD_ENDED);
        }
    }

    private LocalDateTime closesAt(CarpoolDetail detail) {
        return detail.getDepartureAt().plusDays(OPEN_DAYS);
    }

    /** 쓰기는 로그인한 회원 본인만 한다. 봇 위임 토큰은 읽기 전용이다. */
    private Long requireWriter(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.LOGIN_REQUIRED);
        }
        if (userDetails instanceof BotDelegatedUserDetails) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        return userDetails.getMember().getId();
    }

    private Post visiblePost(String publicId) {
        Post post =
                postRepository
                        .findWithMemberAndCategoryByPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private CarpoolDetail detailOf(Post post) {
        return carpoolDetailRepository
                .findById(post.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
