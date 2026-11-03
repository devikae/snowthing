package com.ikae.snowthing.domain.image.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.domain.image.service.ProfileImageLifecycleService;
import com.ikae.snowthing.domain.member.service.MemberService;
import com.ikae.snowthing.domain.member.service.ProfileActionRateLimiter;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final MemberService memberService;
    private final com.ikae.snowthing.domain.image.service.ImageService imageService;
    private final ProfileActionRateLimiter profileActionRateLimiter;
    private final ProfileImageLifecycleService profileImages;
    private final com.ikae.snowthing.domain.image.service.PostImageLifecycleService postImages;
    private final com.ikae.snowthing.domain.image.service.ImageProcessingGate processingGate;
    private final com.ikae.snowthing.global.web.ClientIpResolver clientIpResolver;

    @PostMapping("/keep-alive")
    public ResponseEntity<Void> keepAlive(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
                    com.ikae.snowthing.domain.image.dto.ImageLeaseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        profileActionRateLimiter.check(
                userDetails.getUsername(), ProfileActionRateLimiter.Action.IMAGE_LEASE);
        postImages.renew(request.imageKeys(), userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }
        memberService.getMySettings(userDetails.getUsername());
        var prepared =
                processingGate.run(
                        () -> {
                            profileActionRateLimiter.checkImage(
                                    userDetails.getUsername(),
                                    ProfileActionRateLimiter.Action.PHOTO,
                                    userDetails.getMember().getCreatedAt(),
                                    clientIpResolver.resolve(httpRequest));
                            return imageService.prepareProfileImage(file);
                        });
        ImageUploadResponse response =
                profileImages.uploadPrepared(userDetails.getUsername(), prepared);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }

        var prepared =
                processingGate.run(
                        () -> {
                            profileActionRateLimiter.checkImage(
                                    userDetails.getUsername(),
                                    ProfileActionRateLimiter.Action.POST_IMAGE,
                                    userDetails.getMember().getCreatedAt(),
                                    clientIpResolver.resolve(httpRequest));
                            return imageService.preparePostImage(file);
                        });
        ImageUploadResponse response = postImages.upload(prepared, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
