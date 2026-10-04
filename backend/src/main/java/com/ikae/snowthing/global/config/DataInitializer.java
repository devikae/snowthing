package com.ikae.snowthing.global.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.market.entity.MarketCategory;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.RidingStyle;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.member.repository.RidingStyleRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;

import lombok.RequiredArgsConstructor;

@Component
@Profile("local")
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final ResortRepository resortRepository;
    private final RidingStyleRepository ridingStyleRepository;
    private final PostCategoryRepository categoryRepository;
    private final MarketCategoryRepository marketCategoryRepository;
    private final PostRepository postRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!memberRepository.existsByEmail("user@snowthing.com")
                && !memberRepository.existsByNickname("스노보더1")) {
            memberRepository.save(
                    Member.builder()
                            .email("user@snowthing.com")
                            .password(passwordEncoder.encode("123123"))
                            .nickname("스노보더1")
                            .role(Role.ROLE_USER)
                            .build());
        }

        if (!memberRepository.existsByEmail("admin@snowthing.com")
                && !memberRepository.existsByNickname("최고관리자")) {
            memberRepository.save(
                    Member.builder()
                            .email("admin@snowthing.com")
                            .password(passwordEncoder.encode("Password123!"))
                            .nickname("최고관리자")
                            .role(Role.ROLE_ADMIN)
                            .build());
        }

        List<Resort> targetResorts =
                List.of(
                        resort("PHOENIX", "휘닉스파크", "강원 평창", 1, "37.5805715", "128.3224144"),
                        resort("VIVALDI", "비발디파크", "강원 홍천", 2, "37.6450833", "127.6820210"),
                        resort("HIGH1", "하이원리조트", "강원 정선", 3, "37.2040383", "128.8388351"),
                        resort("YONGPYONG", "모나용평", "강원 평창", 4, "37.6457553", "128.6805217"),
                        resort("WELLI_HILLI", "웰리힐리파크", "강원 횡성", 5, "37.4855523", "128.2477908"),
                        resort("JISAN", "지산리조트", "경기 이천", 6, "37.2167759", "127.3451839"),
                        resort("KONJIAM", "곤지암리조트", "경기 광주", 7, "37.3369199", "127.2935199"),
                        resort("MUJU", "무주덕유산리조트", "전북 무주", 8, "35.8909032", "127.7368635"),
                        resort("EDEN_VALLEY", "에덴밸리리조트", "경남 양산", 9, "35.4248266", "128.9852354"),
                        resort("ELYSIAN", "엘리시안 강촌", "강원 춘천", 10, "37.8211533", "127.5889848"),
                        resort("ALPENSIA", "알펜시아리조트", "강원 평창", 11, "37.6563744", "128.6733956"),
                        resort("OAK_VALLEY", "오크밸리", "강원 원주", 12, "37.4031965", "127.8170565"),
                        resort("O2_RESORT", "오투리조트", "강원 태백", 13, "37.1775331", "128.9480017"));

        for (Resort target : targetResorts) {
            Optional<Resort> byCode = resortRepository.findByCode(target.getCode());
            if (byCode.isPresent()) {
                Resort existing = byCode.get();
                existing.updateRouteCoordinate(
                        target.getRouteLatitude(), target.getRouteLongitude());
                resortRepository.save(existing);
                continue;
            }
            Optional<Resort> byName = resortRepository.findByName(target.getName());
            if (byName.isPresent()) {
                Resort existing = byName.get();
                existing.updateMetadata(
                        target.getCode(), target.getDisplayOrder(), target.isActive());
                existing.updateRouteCoordinate(
                        target.getRouteLatitude(), target.getRouteLongitude());
                resortRepository.save(existing);
            } else {
                resortRepository.save(target);
            }
        }

        if (ridingStyleRepository.count() == 0) {
            ridingStyleRepository.saveAll(
                    List.of(
                            RidingStyle.builder()
                                    .styleName("올라운드")
                                    .description("슬로프, 트릭, 파크 등을 가리지 않고 다양하게 즐기는 스타일")
                                    .build(),
                            RidingStyle.builder()
                                    .styleName("라이딩 / 카빙")
                                    .description("슬로프 고속 라이딩 및 칼날 카빙")
                                    .build(),
                            RidingStyle.builder()
                                    .styleName("그라운드 트릭")
                                    .description("평지 버터링, 알리, 스핀 트릭")
                                    .build(),
                            RidingStyle.builder()
                                    .styleName("파크 / 기물 / 파이프")
                                    .description("킥커 점프, 레일, 하프파이프")
                                    .build(),
                            RidingStyle.builder()
                                    .styleName("입문 / 초보")
                                    .description("기초 자세 및 B턴, J턴 습득 중")
                                    .build(),
                            RidingStyle.builder()
                                    .styleName("관광 / 크루징")
                                    .description("풍경 감상 및 여유로운 라이딩")
                                    .build()));
        }

        if (categoryRepository.count() == 0) {
            categoryRepository.saveAll(
                    List.of(
                            PostCategory.builder().name("자유게시판").code("FREE").build(),
                            PostCategory.builder().name("익명게시판").code("ANONYMOUS").build(),
                            PostCategory.builder().name("질문게시판").code("QNA").build(),
                            PostCategory.builder().name("장비VS").code("GEAR_VS").build(),
                            PostCategory.builder().name("맛집게시판").code("FOOD").build(),
                            PostCategory.builder().name("카풀·동행").code("CARPOOL").build()));
        }

        if (categoryRepository.findByCode("CARPOOL").isEmpty()) {
            categoryRepository.save(PostCategory.builder().name("카풀·동행").code("CARPOOL").build());
        }

        if (categoryRepository.findByCode("MARKET").isEmpty()) {
            categoryRepository.save(PostCategory.builder().name("중고장터").code("MARKET").build());
        }

        if (marketCategoryRepository.count() == 0) {
            marketCategoryRepository.saveAll(
                    List.of(
                            marketCategory("SNOWBOARD", "스노보드", 1),
                            marketCategory("SKI", "스키", 2),
                            marketCategory("BINDING", "바인딩", 3),
                            marketCategory("BOOTS", "부츠", 4),
                            marketCategory("APPAREL", "의류", 5),
                            marketCategory("PROTECTIVE_GEAR", "보호장비", 6),
                            marketCategory("ACCESSORY", "액세서리", 7),
                            marketCategory("PASS", "시즌권·이용권", 8),
                            marketCategory("OTHER", "기타", 9)));
        }

        if (postRepository.count() == 0) {
            PostCategory freeCat = categoryRepository.findByCode("FREE").orElse(null);
            PostCategory anonCat = categoryRepository.findByCode("ANONYMOUS").orElse(null);

            if (freeCat != null && anonCat != null) {
                String encPass = passwordEncoder.encode("1234");
                // 자유게시판 더미 15건
                for (int i = 1; i <= 15; i++) {
                    postRepository.save(
                            Post.builder()
                                    .category(freeCat)
                                    .title("테스트 자유 게시글 " + i)
                                    .content("테스트 자유 게시글 본문 내용 " + i + "입니다. 슬로프 설질 및 장비 정보 공유!")
                                    .writerIp("127.0.0.1")
                                    .isAnonymous(true)
                                    .anonymousPassword(encPass)
                                    .hasImage(i % 2 == 0)
                                    .build());
                }
                // 익명게시판 더미 15건
                for (int i = 1; i <= 15; i++) {
                    postRepository.save(
                            Post.builder()
                                    .category(anonCat)
                                    .title("익명 게시판 테스트 " + i)
                                    .content("익명 게시판 본문 내용 " + i + "입니다. 가감 없는 솔직한 생각 공유!")
                                    .writerIp("127.0.0.1")
                                    .isAnonymous(true)
                                    .anonymousPassword(encPass)
                                    .hasImage(i % 3 == 0)
                                    .build());
                }
            }
        }
    }

    private MarketCategory marketCategory(String code, String name, int sortOrder) {
        return MarketCategory.builder()
                .code(code)
                .name(name)
                .sortOrder(sortOrder)
                .active(true)
                .build();
    }

    private Resort resort(
            String code,
            String name,
            String regionName,
            int displayOrder,
            String routeLatitude,
            String routeLongitude) {
        return Resort.builder()
                .code(code)
                .name(name)
                .regionName(regionName)
                .displayOrder(displayOrder)
                .active(true)
                .routeLatitude(new BigDecimal(routeLatitude))
                .routeLongitude(new BigDecimal(routeLongitude))
                .build();
    }
}
