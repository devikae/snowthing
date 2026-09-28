package com.ikae.snowthing.domain.resortcam.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.resortcam.dto.ResortCameraListResponse;
import com.ikae.snowthing.domain.resortcam.service.ResortCameraQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/resort-cams")
@RequiredArgsConstructor
public class ResortCameraController {

    private final ResortCameraQueryService resortCameraQueryService;

    @GetMapping
    public ResponseEntity<ResortCameraListResponse> getResortCameras() {
        return ResponseEntity.ok(resortCameraQueryService.getActiveResortCameras());
    }
}
