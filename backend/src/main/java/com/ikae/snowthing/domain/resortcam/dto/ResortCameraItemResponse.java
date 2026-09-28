package com.ikae.snowthing.domain.resortcam.dto;

import com.ikae.snowthing.domain.resortcam.entity.CameraSourceType;
import com.ikae.snowthing.domain.resortcam.entity.ResortCamera;

public record ResortCameraItemResponse(
        String code,
        String name,
        CameraSourceType sourceType,
        String sourceUrl,
        String externalPageUrl,
        int displayOrder) {

    public static ResortCameraItemResponse from(ResortCamera camera) {
        return new ResortCameraItemResponse(
                camera.getCode(),
                camera.getName(),
                camera.getSourceType(),
                camera.getSourceUrl(),
                camera.getExternalPageUrl(),
                camera.getDisplayOrder());
    }
}
