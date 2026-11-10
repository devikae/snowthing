package com.ikae.snowthing.domain.member.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.auth.service.AuthService;
import com.ikae.snowthing.domain.auth.service.SocialAuthService;
import com.ikae.snowthing.domain.chat.service.ChatRecentHistoryBuffer;
import com.ikae.snowthing.domain.email.dto.EmailAvailabilityResponse;
import com.ikae.snowthing.domain.email.dto.EmailRequest;
import com.ikae.snowthing.domain.email.service.EmailAvailabilityIpRateLimiter;
import com.ikae.snowthing.domain.email.service.EmailVerificationService;
import com.ikae.snowthing.domain.email.service.VerifiedMemberRegistrationService;
import com.ikae.snowthing.domain.image.service.ProfileImageLifecycleService;
import com.ikae.snowthing.domain.member.dto.MarketingConsentResponse;
import com.ikae.snowthing.domain.member.dto.MarketingConsentUpdateRequest;
import com.ikae.snowthing.domain.member.dto.MemberPasswordChangeRequest;
import com.ikae.snowthing.domain.member.dto.MemberProfileUpdateRequest;
import com.ikae.snowthing.domain.member.dto.MemberSettingsResponse;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.dto.MemberWithdrawalRequest;
import com.ikae.snowthing.domain.member.dto.NicknameCheckRequest;
import com.ikae.snowthing.domain.member.dto.SocialAccountsResponse;
import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.domain.member.service.MarketingConsentService;
import com.ikae.snowthing.domain.member.service.MemberService;
import com.ikae.snowthing.domain.member.service.MemberWithdrawalService;
import com.ikae.snowthing.domain.member.service.ProfileActionRateLimiter;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.MemberSessionRegistry;
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
    private final MemberSessionRegistry memberSessionRegistry;
    private final ProfileActionRateLimiter profileActionRateLimiter;
    private final ProfileImageLifecycleService profileImages;
    private final MarketingConsentService marketingConsentService;
    private final MemberWithdrawalService memberWithdrawalService;
    private final ChatRecentHistoryBuffer chatRecentHistoryBuffer;
    private final SocialAuthService socialAuthService;

    @GetMapping("/me/settings")
    public ResponseEntity<MemberSettingsResponse> getSettings(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(memberService.getMySettings(user.getUsername()));
    }

    @PutMapping("/me/settings")
    public ResponseEntity<MemberSettingsResponse> updateSettings(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody MemberProfileUpdateRequest request) {
        memberService.updateMyProfile(user.getUsername(), request);
        return ResponseEntity.ok(memberService.getMySettings(user.getUsername()));
    }

    @GetMapping("/me/marketing-consent")
    public ResponseEntity<MarketingConsentResponse> getMarketingConsent(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(marketingConsentService.get(user.getUsername()));
    }

    @PutMapping("/me/marketing-consent")
    public ResponseEntity<MarketingConsentResponse> updateMarketingConsent(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody MarketingConsentUpdateRequest request) {
        return ResponseEntity.ok(
                marketingConsentService.update(user.getUsername(), request.agreed()));
    }

    @PostMapping("/me/nickname-availability")
    public ResponseEntity<NicknameAvailability> checkNickname(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody NicknameCheckRequest request) {
        profileActionRateLimiter.check(
                user.getUsername(), ProfileActionRateLimiter.Action.NICKNAME);
        return ResponseEntity.ok(
                new NicknameAvailability(
                        request.nickname(),
                        memberService.isNicknameAvailable(user.getUsername(), request.nickname())));
    }

    public record NicknameAvailability(String nickname, boolean available) {}

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody MemberPasswordChangeRequest request) {
        profileActionRateLimiter.check(
                user.getUsername(), ProfileActionRateLimiter.Action.PASSWORD);
        String publicId = memberService.changePassword(user.getUsername(), request);
        memberSessionRegistry.invalidateAll(publicId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/withdrawal")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody MemberWithdrawalRequest request) {
        profileActionRateLimiter.check(
                user.getUsername(), ProfileActionRateLimiter.Action.WITHDRAWAL);
        String publicId = memberWithdrawalService.withdraw(user.getMember().getId(), request);
        // 트랜잭션이 커밋된 뒤에 끊는다. 실패한 탈퇴가 세션을 끊지 않게 한다.
        memberSessionRegistry.invalidateAll(publicId);
        chatRecentHistoryBuffer.removeSender(publicId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/social-accounts")
    public ResponseEntity<SocialAccountsResponse> getSocialAccounts(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(socialAuthService.getSocialAccounts(user.getMemberId()));
    }

    /** 연결은 OAuth 흐름을 거쳐야 해서 /api/v1/auth/oauth2/link/{provider}에서 시작한다. */
    @DeleteMapping("/me/social-accounts/{provider}")
    public ResponseEntity<Void> unlinkSocialAccount(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable String provider) {
        socialAuthService.unlinkSocialAccount(user.getMemberId(), SocialProvider.from(provider));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/profile-image")
    public ResponseEntity<Void> resetProfileImage(
            @AuthenticationPrincipal CustomUserDetails user, HttpServletRequest httpRequest) {
        profileActionRateLimiter.checkImage(
                user.getUsername(),
                ProfileActionRateLimiter.Action.PHOTO,
                user.getMember().getCreatedAt(),
                clientIpResolver.resolve(httpRequest));
        profileImages.reset(user.getUsername());
        return ResponseEntity.noContent().build();
    }

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
