package com.ikae.snowthing.domain.image.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.image.dto.PostImagePurgeRequest;
import com.ikae.snowthing.domain.image.dto.PostImagePurgeResponse;
import com.ikae.snowthing.domain.image.service.PostImagePurgeService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class PostImageAdminController {

    private final PostImagePurgeService purgeService;

    @PostMapping("/{publicId}/images/purge")
    public ResponseEntity<PostImagePurgeResponse> purge(
            @PathVariable String publicId,
            @Valid @RequestBody PostImagePurgeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(purgeService.purge(publicId, request.reason(), userDetails));
    }
}
