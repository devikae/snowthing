package com.ikae.snowthing.domain.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminPostBlindRequest;
import com.ikae.snowthing.domain.admin.dto.AdminPostResponse;
import com.ikae.snowthing.domain.admin.service.AdminPostService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    @GetMapping
    public ResponseEntity<Page<AdminPostResponse>> getPosts(
            @RequestParam(name = "filter", defaultValue = "ALL") AdminPostService.Filter filter,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(adminPostService.getPosts(filter, category, page, size));
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable("publicId") String publicId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        adminPostService.deletePost(publicId, userDetails);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{publicId}/blind")
    public ResponseEntity<Void> setBlinded(
            @PathVariable("publicId") String publicId, @RequestBody AdminPostBlindRequest request) {
        adminPostService.setBlinded(publicId, request.blinded());
        return ResponseEntity.noContent().build();
    }
}
