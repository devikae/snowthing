package com.ikae.snowthing.domain.admin.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.SystemConfigResponse;
import com.ikae.snowthing.domain.admin.dto.SystemConfigUpdateRequest;
import com.ikae.snowthing.domain.admin.service.SystemConfigService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/configs")
@RequiredArgsConstructor
public class AdminConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    public ResponseEntity<List<SystemConfigResponse>> getAllConfigs() {
        List<SystemConfigResponse> configs = systemConfigService.getAllConfigs();
        return ResponseEntity.ok(configs);
    }

    @PatchMapping("/{configKey}")
    public ResponseEntity<SystemConfigResponse> updateConfig(
            @PathVariable("configKey") String configKey,
            @Valid @RequestBody SystemConfigUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String updatedBy = (userDetails != null && userDetails.getUsername() != null) ? userDetails.getUsername() : "ADMIN";
        SystemConfigResponse response = systemConfigService.updateConfig(configKey, request.value(), updatedBy);
        return ResponseEntity.ok(response);
    }
}
