package com.ikae.snowthing.global.config;

import java.io.InputStream;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortcam.entity.CameraSourceType;
import com.ikae.snowthing.domain.resortcam.entity.ResortCamera;
import com.ikae.snowthing.domain.resortcam.repository.ResortCameraRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Component
@Profile("local")
@Order(2)
@RequiredArgsConstructor
public class ResortCameraDataInitializer implements CommandLineRunner {

    private final ResortRepository resortRepository;
    private final ResortCameraRepository resortCameraRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        if (resortCameraRepository.count() > 0) {
            return;
        }

        ClassPathResource resource = new ClassPathResource("resort-cameras-local.json");
        try (InputStream inputStream = resource.getInputStream()) {
            List<CameraSeed> seeds =
                    objectMapper.readValue(inputStream, new TypeReference<List<CameraSeed>>() {});
            List<ResortCamera> cameras = seeds.stream().map(this::toEntity).toList();
            resortCameraRepository.saveAll(cameras);
        }
    }

    private ResortCamera toEntity(CameraSeed seed) {
        Resort resort =
                resortRepository
                        .findByCode(seed.resortCode())
                        .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
        return ResortCamera.builder()
                .resort(resort)
                .code(seed.code())
                .name(seed.name())
                .sourceType(seed.sourceType())
                .sourceUrl(seed.sourceUrl())
                .externalPageUrl(seed.externalPageUrl())
                .displayOrder(seed.displayOrder())
                .active(true)
                .build();
    }

    private record CameraSeed(
            String resortCode,
            String code,
            String name,
            CameraSourceType sourceType,
            String sourceUrl,
            String externalPageUrl,
            int displayOrder) {}
}
