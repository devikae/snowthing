package com.ikae.snowthing.domain.admin.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.service.SystemConfigService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public/configs")
@RequiredArgsConstructor
public class PublicConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPublicConfigs() {
        return ResponseEntity.ok(systemConfigService.getPublicConfigs());
    }
}
