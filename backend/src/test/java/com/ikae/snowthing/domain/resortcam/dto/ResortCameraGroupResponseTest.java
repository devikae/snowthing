package com.ikae.snowthing.domain.resortcam.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.resortcam.entity.CameraSourceType;

class ResortCameraGroupResponseTest {

    @Test
    @DisplayName("응답 DTO는 전달받은 카메라 목록을 방어적으로 복사한다")
    void camerasAreDefensivelyCopied() {
        List<ResortCameraItemResponse> source = new ArrayList<>();
        source.add(camera("CAM_01"));

        ResortCameraGroupResponse response =
                new ResortCameraGroupResponse("PHOENIX", "휘닉스파크", "강원 평창", 1, source);
        source.add(camera("CAM_02"));

        org.assertj.core.api.Assertions.assertThat(response.cameras()).hasSize(1);
        assertThatThrownBy(() -> response.cameras().add(camera("CAM_03")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ResortCameraItemResponse camera(String code) {
        return new ResortCameraItemResponse(
                code,
                "베이스",
                CameraSourceType.HLS,
                "https://example.com/live.m3u8",
                "https://example.com/webcam",
                1);
    }
}
