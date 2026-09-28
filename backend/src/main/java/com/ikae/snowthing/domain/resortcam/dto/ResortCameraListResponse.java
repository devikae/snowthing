package com.ikae.snowthing.domain.resortcam.dto;

import java.util.List;

public record ResortCameraListResponse(List<ResortCameraGroupResponse> resorts) {

    public ResortCameraListResponse {
        resorts = List.copyOf(resorts);
    }
}
