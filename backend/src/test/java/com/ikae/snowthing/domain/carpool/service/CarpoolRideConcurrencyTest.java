package com.ikae.snowthing.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRiderStatus;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.carpool.repository.CarpoolDetailRepository;
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
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 정원 1명 카풀에서 서로 다른 탑승자를 동시에 확정해도 한 명만 확정된다. 실제 트랜잭션을 쓰므로 @Transactional을 붙이지 않는다. */
@SpringBootTest
class CarpoolRideConcurrencyTest {

    @Autowired private CarpoolReviewService service;
    @Autowired private TransactionTemplate tx;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ResortRepository resortRepository;
    @Autowired private CarpoolDetailRepository carpoolDetailRepository;
    @Autowired private CarpoolRiderRepository riderRepository;

    @Test
    @DisplayName("동시 확정이 정원을 넘지 않는다")
    void concurrentConfirmsRespectCapacity() throws Exception {
        Member driver = tx.execute(status -> member("drv"));
        List<Member> riders = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            riders.add(tx.execute(status -> member("rdr")));
        }
        Post post = tx.execute(status -> carpool(driver));
        for (Member rider : riders) {
            service.requestRide(post.getPublicId(), new CustomUserDetails(rider));
        }

        ExecutorService pool = Executors.newFixedThreadPool(riders.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (Member rider : riders) {
            results.add(
                    pool.submit(
                            () -> {
                                start.await();
                                try {
                                    service.decideRide(
                                            post.getPublicId(),
                                            rider.getPublicId(),
                                            true,
                                            new CustomUserDetails(driver));
                                    return "OK";
                                } catch (CustomException e) {
                                    return e.getErrorCode().name();
                                }
                            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
        assertThat(outcomes)
                .filteredOn(ErrorCode.CARPOOL_RIDE_CAPACITY_FULL.name()::equals)
                .hasSize(riders.size() - 1);
        assertThat(
                        riderRepository.findAll().stream()
                                .filter(r -> r.getStatus() == CarpoolRiderStatus.CONFIRMED)
                                .filter(
                                        r ->
                                                riders.stream()
                                                        .anyMatch(
                                                                m ->
                                                                        m.getId()
                                                                                .equals(
                                                                                        r.getMember()
                                                                                                .getId())))
                                .count())
                .isEqualTo(1);
    }

    private Member member(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return memberRepository.save(
                Member.builder()
                        .publicId(UUID.randomUUID().toString())
                        .email(prefix + suffix + "@snow.com")
                        .nickname(prefix + suffix)
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build());
    }

    private Post carpool(Member driver) {
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
                                .name("동시리조트" + suffix)
                                .regionName("강원")
                                .code("C" + suffix)
                                .displayOrder(1)
                                .build());
        Post saved =
                postRepository.save(
                        Post.builder()
                                .member(driver)
                                .category(category)
                                .title("동시 확정 " + suffix)
                                .content("본문")
                                .writerIp("127.0.0.1")
                                .isAnonymous(false)
                                .build());
        carpoolDetailRepository.save(
                CarpoolDetail.builder()
                        .post(saved)
                        // 커밋되는 데이터라 다른 카풀 검색 테스트에 걸리지 않는 값과 지난 시각을 쓴다.
                        .departureRegion("동시확정출발")
                        .meetingPlace("동시확정집결")
                        .destinationResort(resort)
                        .destinationLatitude(BigDecimal.valueOf(37.5))
                        .destinationLongitude(BigDecimal.valueOf(127.0))
                        .tripType(CarpoolTripType.ONE_WAY)
                        .departureAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1))
                        .passengerCapacity(1)
                        .costMode(CarpoolCostMode.MANUAL)
                        .estimatedCostPerPerson(10000)
                        .build());
        return saved;
    }
}
