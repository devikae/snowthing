package com.ikae.snowthing.domain.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
            @RequestParam(name = "onlyDeleted", defaultValue = "true") boolean onlyDeleted,
            @RequestParam(name = "onlyBlinded", defaultValue = "false") boolean onlyBlinded,
            @PageableDefault(size = 20, sort = "deleted_at", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        Page<AdminPostResponse> posts =
                adminPostService.getPosts(onlyDeleted, onlyBlinded, pageable);
        return ResponseEntity.ok(posts);
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable("publicId") String publicId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long adminMemberId = userDetails != null ? userDetails.getMemberId() : null;
        adminPostService.deletePostByAdmin(publicId, adminMemberId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{publicId}/restore")
    public ResponseEntity<Void> restorePost(@PathVariable("publicId") String publicId) {
        adminPostService.restorePostByAdmin(publicId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{publicId}/blind")
    public ResponseEntity<Void> setBlinded(
            @PathVariable("publicId") String publicId, @RequestBody AdminPostBlindRequest request) {
        adminPostService.toggleBlindPost(publicId, request.blinded());
        return ResponseEntity.noContent().build();
    }
}
