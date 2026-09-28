package com.ikae.snowthing.domain.resortcam.dto;

import java.util.List;

public record ResortCameraGroupResponse(
        String code,
        String name,
        String regionName,
        int displayOrder,
        List<ResortCameraItemResponse> cameras) {

    public ResortCameraGroupResponse {
        cameras = List.copyOf(cameras);
    }
}
