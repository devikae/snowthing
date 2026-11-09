package com.ikae.snowthing.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.carpool.dto.CarpoolMyReviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolParticipationResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolReviewCreateRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolReviewSummaryResponse;
import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReview;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewRating;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewTag;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRiderStatus;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.carpool.repository.CarpoolDetailRepository;
import com.ikae.snowthing.domain.carpool.repository.CarpoolReviewRepository;
import com.ikae.snowthing.domain.carpool.repository.CarpoolRiderRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@Transactional
class CarpoolReviewServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 10, 12, 0);

    @Autowired private PostRepository postRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ResortRepository resortRepository;
    @Autowired private CarpoolDetailRepository carpoolDetailRepository;
    @Autowired private CarpoolRiderRepository riderRepository;
    @Autowired private CarpoolReviewRepository reviewRepository;

    private CarpoolReviewService service;
    private Member driver;
    private Member rider;
    private Member secondRider;
    private Member outsider;
    private Post post;

    @BeforeEach
    void setUp() {
        service = serviceAt(NOW);
        driver = member("driver");
        rider = member("rider");
        secondRider = member("rider2");
        outsider = member("outside");
        // 1시간 전에 출발한 정원 2명 카풀
        post = carpool(NOW.minusHours(1), 2);
    }

    @Test
    @DisplayName("탑승자가 요청하고 운전자가 확정하면 서로 후기를 남길 수 있다")
    void confirmedRiderAndDriverReviewEachOther() {
        service.requestRide(post.getPublicId(), user(rider));
        service.decideRide(post.getPublicId(), rider.getPublicId(), true, user(driver));

        service.writeReview(
                post.getPublicId(),
                review(driver, CarpoolReviewRating.GOOD, List.of(CarpoolReviewTag.SAFE_DRIVING)),
                user(rider));
        service.writeReview(
                post.getPublicId(),
                review(rider, CarpoolReviewRating.GOOD, List.of()),
                user(driver));

        CarpoolParticipationResponse view = service.participation(post.getPublicId(), user(rider));
        assertThat(view.myStatus()).isEqualTo("CONFIRMED");
        assertThat(view.reviewOpen()).isTrue();
        assertThat(view.members())
                .extracting(CarpoolParticipationResponse.Participant::publicId)
                .containsExactly(driver.getPublicId(), rider.getPublicId());
        assertThat(view.members().get(0).reviewedByMe()).isTrue();
    }

    @Test
    @DisplayName("출발 전에는 탑승 확인도 후기도 할 수 없다")
    void rejectsBeforeDeparture() {
        Post future = carpool(NOW.plusMinutes(1), 2);

        assertError(
                () -> service.requestRide(future.getPublicId(), user(rider)),
                ErrorCode.CARPOOL_NOT_DEPARTED);
        assertError(
                () ->
                        service.writeReview(
                                future.getPublicId(),
                                review(rider, CarpoolReviewRating.GOOD, List.of()),
                                user(driver)),
                ErrorCode.CARPOOL_NOT_DEPARTED);
    }

    @Test
    @DisplayName("출발 시각 정각부터 열리고 30일이 지나면 닫힌다")
    void openWindowBoundaries() {
        Post exact = carpool(NOW, 2);
        service.requestRide(exact.getPublicId(), user(rider));

        CarpoolReviewService late = serviceAt(NOW.plusDays(30).plusSeconds(1));
        assertError(
                () -> late.requestRide(exact.getPublicId(), user(secondRider)),
                ErrorCode.CARPOOL_REVIEW_PERIOD_ENDED);
        assertThat(late.participation(exact.getPublicId(), user(rider)).reviewOpen()).isFalse();

        CarpoolReviewService lastMoment = serviceAt(NOW.plusDays(30));
        lastMoment.requestRide(exact.getPublicId(), user(secondRider));
    }

    @Test
    @DisplayName("운전자는 탑승 요청을 할 수 없고, 같은 카풀에 두 번 요청할 수 없다")
    void rejectsDriverAndDuplicateRequest() {
        assertError(
                () -> service.requestRide(post.getPublicId(), user(driver)),
                ErrorCode.CARPOOL_RIDE_DRIVER_NOT_ALLOWED);
        service.requestRide(post.getPublicId(), user(rider));
        assertError(
                () -> service.requestRide(post.getPublicId(), user(rider)),
                ErrorCode.CARPOOL_RIDE_ALREADY_REQUESTED);
    }

    @Test
    @DisplayName("운전자만 확정할 수 있고, 결정은 한 번이며, 정원을 넘겨 확정할 수 없다")
    void decideRules() {
        Post small = carpool(NOW.minusHours(1), 1);
        service.requestRide(small.getPublicId(), user(rider));
        service.requestRide(small.getPublicId(), user(secondRider));

        assertError(
                () ->
                        service.decideRide(
                                small.getPublicId(), rider.getPublicId(), true, user(outsider)),
                ErrorCode.ACCESS_DENIED);
        assertError(
                () ->
                        service.decideRide(
                                small.getPublicId(), outsider.getPublicId(), true, user(driver)),
                ErrorCode.CARPOOL_RIDE_NOT_FOUND);

        service.decideRide(small.getPublicId(), rider.getPublicId(), true, user(driver));
        assertError(
                () ->
                        service.decideRide(
                                small.getPublicId(), rider.getPublicId(), false, user(driver)),
                ErrorCode.CARPOOL_RIDE_ALREADY_DECIDED);
        assertError(
                () ->
                        service.decideRide(
                                small.getPublicId(), secondRider.getPublicId(), true, user(driver)),
                ErrorCode.CARPOOL_RIDE_CAPACITY_FULL);

        service.decideRide(small.getPublicId(), secondRider.getPublicId(), false, user(driver));
        assertThat(
                        riderRepository
                                .findByCarpool_IdAndMember_Id(small.getId(), secondRider.getId())
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(CarpoolRiderStatus.REJECTED);
    }

    @Test
    @DisplayName("같이 타지 않은 사람, 확정 전 탑승자, 거절된 탑승자는 후기를 쓸 수 없다")
    void rejectsNonParticipants() {
        service.requestRide(post.getPublicId(), user(rider));

        assertError(
                () ->
                        service.writeReview(
                                post.getPublicId(),
                                review(driver, CarpoolReviewRating.GOOD, List.of()),
                                user(outsider)),
                ErrorCode.CARPOOL_REVIEW_NOT_PARTICIPANT);
        assertError(
                () ->
                        service.writeReview(
                                post.getPublicId(),
                                review(driver, CarpoolReviewRating.GOOD, List.of()),
                                user(rider)),
                ErrorCode.CARPOOL_REVIEW_NOT_PARTICIPANT);
        assertError(
                () ->
                        service.writeReview(
                                post.getPublicId(),
                                review(rider, CarpoolReviewRating.GOOD, List.of()),
                                user(driver)),
                ErrorCode.CARPOOL_REVIEW_NOT_PARTICIPANT);

        service.decideRide(post.getPublicId(), rider.getPublicId(), false, user(driver));
        assertError(
                () ->
                        service.writeReview(
                                post.getPublicId(),
                                review(driver, CarpoolReviewRating.GOOD, List.of()),
                                user(rider)),
                ErrorCode.CARPOOL_REVIEW_NOT_PARTICIPANT);
    }

    @Test
    @DisplayName("본인 후기와 같은 대상 두 번째 후기는 거절된다")
    void rejectsSelfAndDuplicate() {
        confirm(rider);
        assertError(
                () ->
                        service.writeReview(
                                post.getPublicId(),
                                review(rider, CarpoolReviewRating.GOOD, List.of()),
                                user(rider)),
                ErrorCode.CARPOOL_REVIEW_SELF_NOT_ALLOWED);

        service.writeReview(
                post.getPublicId(),
                review(driver, CarpoolReviewRating.BAD, List.of()),
                user(rider));
        assertError(
                () ->
                        service.writeReview(
                                post.getPublicId(),
                                review(driver, CarpoolReviewRating.GOOD, List.of()),
                                user(rider)),
                ErrorCode.CARPOOL_REVIEW_DUPLICATED);
    }

    @Test
    @DisplayName("DB 유니크 제약이 같은 (카풀, 작성자, 대상) 후기를 막는다")
    void uniqueConstraintBlocksDuplicateRows() {
        CarpoolDetail detail = carpoolDetailRepository.findById(post.getId()).orElseThrow();
        reviewRepository.saveAndFlush(
                new CarpoolReview(detail, rider, driver, CarpoolReviewRating.GOOD, Set.of(), null));

        assertThatThrownBy(
                        () ->
                                reviewRepository.saveAndFlush(
                                        new CarpoolReview(
                                                detail,
                                                rider,
                                                driver,
                                                CarpoolReviewRating.BAD,
                                                Set.of(),
                                                null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("아쉬웠어요에는 태그를 붙일 수 없고, 운전 태그는 운전자에게만 붙인다")
    void validatesTags() {
        confirm(rider);
        confirm(secondRider);
        assertError(
                () ->
                        service.writeReview(
                                post.getPublicId(),
                                review(
                                        driver,
                                        CarpoolReviewRating.BAD,
                                        List.of(CarpoolReviewTag.KIND)),
                                user(rider)),
                ErrorCode.CARPOOL_REVIEW_INVALID_TAG);
        assertError(
                () ->
                        service.writeReview(
                                post.getPublicId(),
                                review(
                                        secondRider,
                                        CarpoolReviewRating.GOOD,
                                        List.of(CarpoolReviewTag.CLEAN_CAR)),
                                user(rider)),
                ErrorCode.CARPOOL_REVIEW_INVALID_TAG);
    }

    @Test
    @DisplayName("비회원은 쓸 수 없고 봇 위임 토큰도 쓸 수 없다")
    void rejectsGuestAndBot() {
        assertError(
                () -> service.requestRide(post.getPublicId(), null), ErrorCode.INVALID_CREDENTIALS);
        assertError(
                () -> service.requestRide(post.getPublicId(), new BotDelegatedUserDetails(rider)),
                ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("함께 탄 사람 목록은 참여자에게만, 대기 요청은 운전자에게만 보인다")
    void participationVisibility() {
        confirm(rider);
        service.requestRide(post.getPublicId(), user(secondRider));

        assertThat(service.participation(post.getPublicId(), null).members()).isEmpty();
        assertThat(service.participation(post.getPublicId(), null).myStatus()).isNull();
        assertThat(service.participation(post.getPublicId(), user(outsider)).members()).isEmpty();

        CarpoolParticipationResponse waiting =
                service.participation(post.getPublicId(), user(secondRider));
        assertThat(waiting.myStatus()).isEqualTo("REQUESTED");
        assertThat(waiting.members()).isEmpty();

        assertThat(service.participation(post.getPublicId(), user(rider)).members()).hasSize(2);
        CarpoolParticipationResponse driverView =
                service.participation(post.getPublicId(), user(driver));
        assertThat(driverView.myStatus()).isEqualTo("DRIVER");
        assertThat(driverView.confirmedCount()).isEqualTo(1);
        assertThat(driverView.members())
                .extracting(CarpoolParticipationResponse.Participant::status)
                .containsExactly(
                        CarpoolRiderStatus.CONFIRMED,
                        CarpoolRiderStatus.CONFIRMED,
                        CarpoolRiderStatus.REQUESTED);
    }

    @Test
    @DisplayName("공개 요약은 좋았어요만 세고, 본인 요약에는 전체 수와 받은 후기가 있다")
    void summaries() {
        confirm(rider);
        confirm(secondRider);
        service.writeReview(
                post.getPublicId(),
                review(
                        driver,
                        CarpoolReviewRating.GOOD,
                        List.of(CarpoolReviewTag.ON_TIME, CarpoolReviewTag.SAFE_DRIVING)),
                user(rider));
        CarpoolReviewCreateRequest bad =
                new CarpoolReviewCreateRequest(
                        driver.getPublicId(), CarpoolReviewRating.BAD, null, "  조금 늦었어요  ");
        service.writeReview(post.getPublicId(), bad, user(secondRider));

        CarpoolReviewSummaryResponse summary = service.summary(driver.getPublicId());
        assertThat(summary.rideCount()).isEqualTo(1);
        assertThat(summary.goodCount()).isEqualTo(1);
        assertThat(summary.topTags())
                .extracting(CarpoolReviewSummaryResponse.TagCount::tag)
                .containsExactly(CarpoolReviewTag.ON_TIME, CarpoolReviewTag.SAFE_DRIVING);
        assertThat(service.summary(rider.getPublicId()).rideCount()).isEqualTo(1);
        assertThat(service.summary(outsider.getPublicId()).rideCount()).isZero();

        CarpoolMyReviewResponse mine = service.mine(user(driver));
        assertThat(mine.reviewCount()).isEqualTo(2);
        assertThat(mine.goodCount()).isEqualTo(1);
        assertThat(mine.received())
                .extracting(CarpoolMyReviewResponse.Received::comment)
                .contains("조금 늦었어요");
    }

    @Test
    @DisplayName("삭제된 카풀의 기록은 세지 않고, 탈퇴한 회원은 요약도 후기 대상도 아니다")
    void excludesDeletedCarpoolAndWithdrawnMember() {
        confirm(rider);
        service.writeReview(
                post.getPublicId(),
                review(driver, CarpoolReviewRating.GOOD, List.of()),
                user(rider));
        post.softDelete();
        postRepository.saveAndFlush(post);
        assertThat(service.summary(driver.getPublicId()).rideCount()).isZero();
        assertThat(service.summary(driver.getPublicId()).goodCount()).isZero();

        Post other = carpool(NOW.minusHours(1), 2);
        service.requestRide(other.getPublicId(), user(rider));
        service.decideRide(other.getPublicId(), rider.getPublicId(), true, user(driver));
        rider.withdraw("withdrawn-" + rider.getId() + "@withdrawn.invalid", "withdrawn_x");
        memberRepository.saveAndFlush(rider);

        String riderPublicId = rider.getPublicId();
        assertError(() -> service.summary(riderPublicId), ErrorCode.MEMBER_NOT_FOUND);
        assertError(
                () ->
                        service.writeReview(
                                other.getPublicId(),
                                new CarpoolReviewCreateRequest(
                                        riderPublicId, CarpoolReviewRating.GOOD, null, null),
                                user(driver)),
                ErrorCode.MEMBER_NOT_FOUND);
    }

    private void confirm(Member member) {
        service.requestRide(post.getPublicId(), user(member));
        service.decideRide(post.getPublicId(), member.getPublicId(), true, user(driver));
    }

    private CarpoolReviewCreateRequest review(
            Member target, CarpoolReviewRating rating, List<CarpoolReviewTag> tags) {
        return new CarpoolReviewCreateRequest(target.getPublicId(), rating, tags, null);
    }

    private static void assertError(Executable executable, ErrorCode expected) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private CarpoolReviewService serviceAt(LocalDateTime now) {
        return new CarpoolReviewService(
                postRepository,
                carpoolDetailRepository,
                riderRepository,
                reviewRepository,
                memberRepository,
                Clock.fixed(now.atZone(KST).toInstant(), KST));
    }

    private CustomUserDetails user(Member member) {
        return new CustomUserDetails(member);
    }

    private Member member(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return memberRepository.save(
                Member.builder()
                        .publicId(UUID.randomUUID().toString())
                        .email(prefix + suffix + "@snow.com")
                        .nickname(prefix.substring(0, 3) + suffix)
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build());
    }

    private Post carpool(LocalDateTime departureAt, int capacity) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        PostCategory category =
                categoryRepository
                        .findByCode("CARPOOL")
                        .orElseGet(
                                () ->
                                        categoryRepository.save(
                                                PostCategory.builder()
                                                        .name("카풀")
                                                        .code("CARPOOL")
                                                        .build()));
        Resort resort =
                resortRepository.save(
                        Resort.builder()
                                .name("후기리조트" + suffix)
                                .regionName("강원")
                                .code("R" + suffix)
                                .displayOrder(1)
                                .build());
        Post saved =
                postRepository.save(
                        Post.builder()
                                .member(driver)
                                .category(category)
                                .title("후기 카풀 " + suffix)
                                .content("본문")
                                .writerIp("127.0.0.1")
                                .isAnonymous(false)
                                .build());
        carpoolDetailRepository.saveAndFlush(
                CarpoolDetail.builder()
                        .post(saved)
                        .departureRegion("잠실")
                        .meetingPlace("2번 출구")
                        .destinationResort(resort)
                        .destinationLatitude(BigDecimal.valueOf(37.5))
                        .destinationLongitude(BigDecimal.valueOf(127.0))
                        .tripType(CarpoolTripType.ONE_WAY)
                        .departureAt(departureAt)
                        .passengerCapacity(capacity)
                        .costMode(CarpoolCostMode.MANUAL)
                        .estimatedCostPerPerson(10000)
                        .build());
        return saved;
    }
}
