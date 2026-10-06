package com.ikae.snowthing.domain.member.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.auth.service.AuthService;
import com.ikae.snowthing.domain.email.dto.EmailAvailabilityResponse;
import com.ikae.snowthing.domain.email.dto.EmailRequest;
import com.ikae.snowthing.domain.email.service.EmailAvailabilityIpRateLimiter;
import com.ikae.snowthing.domain.email.service.EmailVerificationService;
import com.ikae.snowthing.domain.email.service.VerifiedMemberRegistrationService;
import com.ikae.snowthing.domain.member.dto.MemberProfileUpdateRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.service.MemberService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final AuthService authService;
    private final VerifiedMemberRegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final EmailAvailabilityIpRateLimiter emailAvailabilityIpRateLimiter;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public ResponseEntity<MemberSignUpResponse> signUp(
            @Valid @RequestBody MemberSignUpRequest request) {
        MemberSignUpResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/email-availability")
    public ResponseEntity<EmailAvailabilityResponse> checkEmailAvailability(
            @Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        emailAvailabilityIpRateLimiter.check(clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(
                new EmailAvailabilityResponse(
                        emailVerificationService.isEmailAvailable(request.email())));
    }

    @GetMapping("/me")
    public ResponseEntity<MemberLoginResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        MemberLoginResponse profile =
                authService.getMemberProfileByEmail(userDetails.getUsername());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    public ResponseEntity<MemberLoginResponse> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MemberProfileUpdateRequest request) {
        String email = userDetails.getUsername();
        memberService.updateMyProfile(email, request);
        MemberLoginResponse updatedProfile = authService.getMemberProfileByEmail(email);
        return ResponseEntity.ok(updatedProfile);
    }
}
