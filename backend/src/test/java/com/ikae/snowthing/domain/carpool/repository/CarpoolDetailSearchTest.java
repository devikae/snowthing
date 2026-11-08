package com.ikae.snowthing.domain.carpool.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.carpool.service.CarpoolService;
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

@SpringBootTest
@Transactional
class CarpoolDetailSearchTest {

    @Autowired private CarpoolService carpoolService;
    @Autowired private CarpoolDetailRepository carpoolDetailRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ResortRepository resortRepository;

    private Post visible;

    @BeforeEach
    void setUp() {
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
        Member member =
                memberRepository.save(
                        Member.builder()
                                .publicId(UUID.randomUUID().toString())
                                .email(suffix + "@snow.com")
                                .nickname("카풀" + suffix)
                                .role(Role.ROLE_USER)
                                .status(MemberStatus.ACTIVE)
                                .build());
        Resort resort =
                resortRepository.save(
                        Resort.builder()
                                .name("검색리조트" + suffix)
                                .regionName("강원")
                                .code("S" + suffix)
                                .displayOrder(1)
                                .build());
        visible =
                saveCarpool(
                        category,
                        member,
                        resort,
                        "Weekend 왕복 100% 모집",
                        "잠실역",
                        "2번 출구",
                        LocalDateTime.now().plusDays(3));
        // 출발이 지난 글은 검색해도 나오지 않는다.
        saveCarpool(
                category,
                member,
                resort,
                "Weekend 지난 모집",
                "잠실역",
                "2번 출구",
                LocalDateTime.now().minusDays(1));
        keywordResort = resort.getName();
    }

    private String keywordResort;

    private Post saveCarpool(
            PostCategory category,
            Member member,
            Resort resort,
            String title,
            String region,
            String place,
            LocalDateTime departureAt) {
        Post post =
                postRepository.save(
                        Post.builder()
                                .member(member)
                                .category(category)
                                .title(title)
                                .content("본문에는 장비적재 가능이라고 적었어요")
                                .writerIp("127.0.0.1")
                                .isAnonymous(false)
                                .build());
        carpoolDetailRepository.saveAndFlush(
                CarpoolDetail.builder()
                        .post(post)
                        .departureRegion(region)
                        .meetingPlace(place)
                        .destinationResort(resort)
                        .destinationLatitude(BigDecimal.valueOf(37.5))
                        .destinationLongitude(BigDecimal.valueOf(127.0))
                        .tripType(CarpoolTripType.ONE_WAY)
                        .departureAt(departureAt)
                        .passengerCapacity(2)
                        .costMode(CarpoolCostMode.MANUAL)
                        .estimatedCostPerPerson(10000)
                        .build());
        return post;
    }

    private long total(String keyword) {
        return carpoolService.findPage(0, 20, keyword).getTotalElements();
    }

    @Test
    @DisplayName("제목, 본문, 출발지, 집결지, 리조트에서 찾고 출발이 지난 글은 뺀다")
    void searchesVisibleFields() {
        assertThat(total("weekend")).isEqualTo(1L);
        assertThat(total("장비적재")).isEqualTo(1L);
        assertThat(total("잠실역")).isEqualTo(1L);
        assertThat(total("2번 출구")).isEqualTo(1L);
        assertThat(total(keywordResort)).isEqualTo(1L);
        assertThat(total("없는검색어zzqq")).isZero();
        assertThat(carpoolService.findPage(0, 20, "weekend").getContent().get(0).publicId())
                .isEqualTo(visible.getPublicId());
    }

    @Test
    @DisplayName("퍼센트와 밑줄은 와일드카드가 아니라 글자로 찾는다")
    void escapesWildcards() {
        assertThat(total("100%")).isEqualTo(1L);
        assertThat(total("1_0")).isZero();
    }

    @Test
    @DisplayName("검색어가 비면 전체 목록을 돌려준다")
    void blankKeywordListsAll() {
        assertThat(total("  ")).isEqualTo(total(null));
    }
}
