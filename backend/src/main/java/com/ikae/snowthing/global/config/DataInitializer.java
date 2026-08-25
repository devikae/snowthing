package com.ikae.snowthing.global.config;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.RidingStyle;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.member.repository.RidingStyleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ResortRepository resortRepository;
    private final RidingStyleRepository ridingStyleRepository;

    @Override
    public void run(String... args) {
        if (resortRepository.count() == 0) {
            resortRepository.saveAll(List.of(
                    Resort.builder().name("휘닉스파크").regionName("강원 평창").build(),
                    Resort.builder().name("하이원리조트").regionName("강원 정선").build(),
                    Resort.builder().name("모나용평").regionName("강원 평창").build(),
                    Resort.builder().name("비발디파크").regionName("강원 홍천").build(),
                    Resort.builder().name("웰리힐리파크").regionName("강원 횡성").build(),
                    Resort.builder().name("지산리조트").regionName("경기 이천").build()
            ));
        }

        if (ridingStyleRepository.count() == 0) {
            ridingStyleRepository.saveAll(List.of(
                    RidingStyle.builder().styleName("올라운드").description("슬로프, 트릭, 파크 등을 가리지 않고 다양하게 즐기는 스타일").build(),
                    RidingStyle.builder().styleName("라이딩 / 카빙").description("슬로프 고속 라이딩 및 칼날 카빙").build(),
                    RidingStyle.builder().styleName("그라운드 트릭").description("평지 버터링, 알리, 스핀 트릭").build(),
                    RidingStyle.builder().styleName("파크 / 기물 / 파이프").description("킥커 점프, 레일, 하프파이프").build(),
                    RidingStyle.builder().styleName("입문 / 초보").description("기초 자세 및 B턴, J턴 습득 중").build(),
                    RidingStyle.builder().styleName("관광 / 크루징").description("풍경 감상 및 여유로운 라이딩").build()
            ));
        }
    }
}
