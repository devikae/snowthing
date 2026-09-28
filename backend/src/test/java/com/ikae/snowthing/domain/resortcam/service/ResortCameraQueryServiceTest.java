package com.ikae.snowthing.domain.resortcam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortcam.dto.ResortCameraGroupResponse;
import com.ikae.snowthing.domain.resortcam.dto.ResortCameraListResponse;
import com.ikae.snowthing.domain.resortcam.entity.CameraSourceType;
import com.ikae.snowthing.domain.resortcam.entity.ResortCamera;
import com.ikae.snowthing.domain.resortcam.repository.ResortCameraRepository;

@ExtendWith(MockitoExtension.class)
class ResortCameraQueryServiceTest {

    @Mock private ResortRepository resortRepository;
    @Mock private ResortCameraRepository resortCameraRepository;

    private ResortCameraQueryService service;

    @BeforeEach
    void setUp() {
        service = new ResortCameraQueryService(resortRepository, resortCameraRepository);
    }

    @Test
    @DisplayName("활성 리조트 순서와 각 리조트의 활성 카메라 목록을 묶어 반환한다")
    void getActiveResortCameras() {
        Resort phoenix = resort("PHOENIX", "휘닉스파크", 1);
        Resort high1 = resort("HIGH1", "하이원리조트", 2);
        ResortCamera camera = camera(phoenix);

        given(resortRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc())
                .willReturn(List.of(phoenix, high1));
        given(resortCameraRepository.findAllActiveOrdered()).willReturn(List.of(camera));

        ResortCameraListResponse response = service.getActiveResortCameras();
        List<ResortCameraGroupResponse> result = response.resorts();

        assertThat(result).extracting(ResortCameraGroupResponse::code).containsExactly("PHOENIX");
        assertThat(result.get(0).cameras()).hasSize(1);
    }

    private Resort resort(String code, String name, int displayOrder) {
        return Resort.builder()
                .code(code)
                .name(name)
                .regionName("강원")
                .displayOrder(displayOrder)
                .active(true)
                .build();
    }

    private ResortCamera camera(Resort resort) {
        return ResortCamera.builder()
                .resort(resort)
                .code("CAM_01")
                .name("베이스")
                .sourceType(CameraSourceType.HLS)
                .sourceUrl("https://example.com/live.m3u8")
                .externalPageUrl("https://example.com/webcam")
                .displayOrder(1)
                .active(true)
                .build();
    }
}
