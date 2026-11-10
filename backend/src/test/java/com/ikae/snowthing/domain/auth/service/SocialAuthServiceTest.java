package com.ikae.snowthing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.auth.dto.SocialSignUpRequest;
import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberSocialAccount;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.domain.member.event.MemberSignedUpEvent;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberSocialAccountRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.domain.member.service.RegistrationConsentService;
import com.ikae.snowthing.domain.member.service.WithdrawnEmailService;
import com.ikae.snowthing.domain.member.validation.NicknamePolicy;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MemberSocialAccountRepository memberSocialAccountRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RegistrationConsentService consentService;
    @Mock private WithdrawnEmailService withdrawnEmails;

    private static final SocialSignUpRequest CONSENT = SocialSignUpRequest.builder().build();

    private SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        Clock fixedClock =
                Clock.fixed(Instant.parse("2026-10-19T05:00:00Z"), ZoneId.of("Asia/Seoul"));
        socialAuthService =
                new SocialAuthService(
                        memberRepository,
                        memberSocialAccountRepository,
                        new EmailNormalizer(),
                        new SocialNicknameGenerator(),
                        fixedClock,
                        eventPublisher,
                        consentService,
                        withdrawnEmails);
    }

    private Member completeSignUp(OAuthUserProfile profile) {
        return socialAuthService.completeSignUp(profile, CONSENT);
    }

    @Test
    @DisplayName("처음 온 소셜 사용자는 콜백에서 회원을 만들지 않는다")
    void findMemberForLogin_NewUser_DoesNotCreateMember() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER, "naver-new", "new@naver.com", false, "새보더");
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.NAVER, "naver-new"))
                .willReturn(Optional.empty());

        assertThat(socialAuthService.findMemberForLogin(profile)).isEmpty();

        verify(memberRepository, never()).saveAndFlush(any());
        verify(memberSocialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("탈퇴 후 30일이 지나지 않은 이메일은 동의 화면으로 보내기 전에 거부한다")
    void findMemberForLogin_WithdrawnEmail_IsRejected() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE, "google-new", "left@gmail.com", true, "구글보더");
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE, "google-new"))
                .willReturn(Optional.empty());
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.WITHDRAWN_EMAIL_RESTRICTED))
                .given(withdrawnEmails)
                .requireNotBlocked("left@gmail.com");

        assertThatThrownBy(() -> socialAuthService.findMemberForLogin(profile))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWN_EMAIL_RESTRICTED);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 동의 화면으로 보내기 전에 거부한다")
    void findMemberForLogin_ExistingEmail_RequiresAccountLink() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE, "google-new", "used@gmail.com", true, "구글보더");
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE, "google-new"))
                .willReturn(Optional.empty());
        given(memberRepository.findByEmail("used@gmail.com"))
                .willReturn(Optional.of(mock(Member.class)));

        assertThatThrownBy(() -> socialAuthService.findMemberForLogin(profile))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OAUTH_ACCOUNT_LINK_REQUIRED);
    }

    @Test
    @DisplayName("동의 검증에 실패하면 회원을 만들지 않는다")
    void completeSignUp_InvalidConsent_DoesNotCreateMember() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER, "naver-new", "new@naver.com", false, "새보더");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.INVALID_INPUT))
                .given(consentService)
                .validate(CONSENT);

        assertThatThrownBy(() -> completeSignUp(profile))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("가입을 마치면 새 회원의 동의 기록을 함께 저장한다")
    void completeSignUp_NewUser_RecordsConsent() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER, "naver-new", "new@naver.com", false, "새보더");
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.NAVER, "naver-new"))
                .willReturn(Optional.empty());
        given(memberRepository.saveAndFlush(any(Member.class)))
                .willAnswer(
                        invocation -> {
                            Member saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 11L);
                            return saved;
                        });

        completeSignUp(profile);

        verify(consentService)
                .record(
                        org.mockito.ArgumentMatchers.eq(11L),
                        org.mockito.ArgumentMatchers.eq(CONSENT),
                        any());
    }

    @Test
    @DisplayName("신규 소셜 로그인 시 비밀번호 없는 활성 회원이 자동 생성되고 소셜 계정이 연결된다")
    void authenticateOrRegister_NewSocialUser_CreatesMemberAndSocialAccount() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER, "naver-1001", "NewRider@Naver.com", false, "네이버보더");

        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.NAVER, "naver-1001"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("네이버보더")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class)))
                .willAnswer(
                        invocation -> {
                            Member saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 10L);
                            return saved;
                        });

        Member member = completeSignUp(profile);

        assertThat(member.getId()).isEqualTo(10L);
        assertThat(member.getEmail()).matches("oauth-naver-[A-Za-z0-9_-]{43}@users\\.invalid");
        assertThat(member.getPassword()).isNull();
        assertThat(member.getNickname()).isEqualTo("네이버보더");
        assertThat(member.getEmailVerifiedAt()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);

        ArgumentCaptor<MemberSocialAccount> socialCaptor =
                ArgumentCaptor.forClass(MemberSocialAccount.class);
        verify(memberSocialAccountRepository).saveAndFlush(socialCaptor.capture());
        assertThat(socialCaptor.getValue().getProvider()).isEqualTo(SocialProvider.NAVER);
        assertThat(socialCaptor.getValue().getProviderUserId()).isEqualTo("naver-1001");
        assertThat(socialCaptor.getValue().getProviderEmail()).isEqualTo("newrider@naver.com");
        verify(memberRepository, never()).findByEmail("newrider@naver.com");
        verify(eventPublisher).publishEvent(new MemberSignedUpEvent("네이버보더", "NAVER"));
    }

    @Test
    @DisplayName("권위 있는 Google 이메일로 신규 가입하면 SnowThing 이메일 인증 시각을 기록한다")
    void authenticateOrRegister_AuthoritativeGoogleEmail_MarksEmailVerified() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE,
                        "google-authoritative",
                        "rider@gmail.com",
                        true,
                        "구글보더");
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE, "google-authoritative"))
                .willReturn(Optional.empty());
        given(memberRepository.findByEmail("rider@gmail.com")).willReturn(Optional.empty());
        given(memberRepository.existsByNickname("구글보더")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Member member = completeSignUp(profile);

        assertThat(member.getEmail()).isEqualTo("rider@gmail.com");
        assertThat(member.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("미연결 소셜 이메일이 기존 회원과 같으면 자동 연결하지 않는다")
    void authenticateOrRegister_ExistingEmail_RequiresExplicitAccountLink() {
        Member existingMember =
                Member.builder()
                        .email("rider@snowthing.org")
                        .password("encoded-password")
                        .nickname("기존보더")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        ReflectionTestUtils.setField(existingMember, "id", 7L);

        OAuthUserProfile googleProfile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE,
                        "google-sub-555",
                        "rider@snowthing.org",
                        true,
                        "구글이름");

        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE, "google-sub-555"))
                .willReturn(Optional.empty());
        given(memberRepository.findByEmail("rider@snowthing.org"))
                .willReturn(Optional.of(existingMember));
        assertThatThrownBy(() -> completeSignUp(googleProfile))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_ACCOUNT_LINK_REQUIRED);

        assertThat(existingMember.getEmailVerifiedAt()).isNull();
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
        verify(memberSocialAccountRepository, never()).saveAndFlush(any(MemberSocialAccount.class));
    }

    @Test
    @DisplayName("소셜 닉네임이 이미 사용 중이면 난수 접미사가 붙은 고유 닉네임으로 가입된다")
    void authenticateOrRegister_DuplicateNickname_GeneratesUniqueNickname() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER, "naver-999", "second@naver.com", false, "설원라이더");

        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.NAVER, "naver-999"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("설원라이더")).willReturn(true);
        given(memberRepository.saveAndFlush(any(Member.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Member created = completeSignUp(profile);

        assertThat(created.getNickname())
                .isNotEqualTo("설원라이더")
                .startsWith("설원라이더")
                .matches(NicknamePolicy.REGEXP);
    }

    @Test
    @DisplayName("미연결 소셜 프로필에 이메일이 누락되면 가입이 거부된다")
    void authenticateOrRegister_MissingEmail_ThrowsCustomException() {
        OAuthUserProfile missingEmailProfile =
                new OAuthUserProfile(SocialProvider.NAVER, "naver-200", null, false, "보더");

        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.NAVER, "naver-200"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> completeSignUp(missingEmailProfile))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_EMAIL_REQUIRED);
    }

    @Test
    @DisplayName("이미 연결된 소셜 계정은 현재 프로필 이메일이 없어도 제공자 고유 ID로 로그인한다")
    void authenticateOrRegister_LinkedAccount_DoesNotRequireEmailProfile() {
        Member linkedMember =
                Member.builder()
                        .email("linked@snowthing.org")
                        .password(null)
                        .nickname("연결회원")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        MemberSocialAccount socialAccount =
                MemberSocialAccount.builder()
                        .member(linkedMember)
                        .provider(SocialProvider.NAVER)
                        .providerUserId("naver-201")
                        .providerEmail("old@naver.com")
                        .build();
        OAuthUserProfile profile =
                new OAuthUserProfile(SocialProvider.NAVER, "naver-201", null, false, "보더");
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.NAVER, "naver-201"))
                .willReturn(Optional.of(socialAccount));

        Member authenticated = completeSignUp(profile);

        assertThat(authenticated).isSameAs(linkedMember);
        assertThat(authenticated.getEmailVerifiedAt()).isNull();
        verify(memberRepository, never()).findByEmail(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("정지되었거나 탈퇴한 회원은 소셜 로그인이 차단된다")
    void authenticateOrRegister_SuspendedOrWithdrawnMember_ThrowsCustomException() {
        Member suspendedMember =
                Member.builder()
                        .email("suspended@snowthing.org")
                        .password(null)
                        .nickname("정지회원")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.SUSPENDED)
                        .build();
        Member withdrawnMember =
                Member.builder()
                        .email("withdrawn@snowthing.org")
                        .password(null)
                        .nickname("탈퇴회원")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.WITHDRAWN)
                        .build();

        OAuthUserProfile suspendedProfile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE,
                        "google-suspended",
                        "suspended@snowthing.org",
                        true,
                        "정지회원");
        OAuthUserProfile withdrawnProfile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE,
                        "google-withdrawn",
                        "withdrawn@snowthing.org",
                        true,
                        "탈퇴회원");
        MemberSocialAccount suspendedSocialAccount =
                MemberSocialAccount.builder()
                        .member(suspendedMember)
                        .provider(SocialProvider.GOOGLE)
                        .providerUserId("google-suspended")
                        .providerEmail("suspended@snowthing.org")
                        .build();
        MemberSocialAccount withdrawnSocialAccount =
                MemberSocialAccount.builder()
                        .member(withdrawnMember)
                        .provider(SocialProvider.GOOGLE)
                        .providerUserId("google-withdrawn")
                        .providerEmail("withdrawn@snowthing.org")
                        .build();
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE, "google-suspended"))
                .willReturn(Optional.of(suspendedSocialAccount));
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE, "google-withdrawn"))
                .willReturn(Optional.of(withdrawnSocialAccount));

        assertThatThrownBy(() -> completeSignUp(suspendedProfile))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        assertThatThrownBy(() -> completeSignUp(withdrawnProfile))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);
    }

    @Test
    @DisplayName("동시 가입으로 닉네임 유니크 제약이 충돌하면 같은 트랜잭션에서 재시도하지 않는다")
    void authenticateOrRegister_NicknameConstraintViolation_ThrowsDuplicateNickname() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE,
                        "google-concurrent",
                        "concurrent@snowthing.org",
                        true,
                        "동시가입자");
        DataIntegrityViolationException violation =
                constraintViolation(Member.NICKNAME_UNIQUE_CONSTRAINT);

        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE, "google-concurrent"))
                .willReturn(Optional.empty());
        given(memberRepository.findByEmail("concurrent@snowthing.org"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("동시가입자")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(violation);

        assertThatThrownBy(() -> completeSignUp(profile))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        verify(memberRepository).saveAndFlush(any(Member.class));
    }

    @Test
    @DisplayName("동시 가입으로 이메일 유니크 제약이 충돌하면 자동 연결 대신 계정 연결 필요 오류를 반환한다")
    void authenticateOrRegister_EmailConstraintViolation_RequiresExplicitAccountLink() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE,
                        "google-email-concurrent",
                        "concurrent-email@snowthing.org",
                        true,
                        "동시가입자");
        DataIntegrityViolationException violation =
                constraintViolation(Member.EMAIL_UNIQUE_CONSTRAINT);

        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE, "google-email-concurrent"))
                .willReturn(Optional.empty());
        given(memberRepository.findByEmail("concurrent-email@snowthing.org"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("동시가입자")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(violation);

        assertThatThrownBy(() -> completeSignUp(profile))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_ACCOUNT_LINK_REQUIRED);
    }

    @Test
    @DisplayName("비권위 프로필의 내부 이메일 유니크 제약 충돌은 계정 연결 안내로 오분류하지 않는다")
    void authenticateOrRegister_InternalEmailConstraintViolation_ThrowsOAuthError() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER,
                        "naver-internal-concurrent",
                        "untrusted@naver.com",
                        false,
                        "내부충돌");
        DataIntegrityViolationException violation =
                constraintViolation(Member.EMAIL_UNIQUE_CONSTRAINT);
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.NAVER, "naver-internal-concurrent"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("내부충돌")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(violation);

        assertThatThrownBy(() -> completeSignUp(profile))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
    }

    @Test
    @DisplayName("신규 가입 중 소셜 계정 유니크 제약이 충돌하면 OAuth 인증 오류로 변환한다")
    void authenticateOrRegister_SocialAccountConstraintViolation_ThrowsOAuthError() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER,
                        "naver-social-concurrent",
                        "social-concurrent@naver.com",
                        false,
                        "소셜충돌");
        DataIntegrityViolationException violation =
                constraintViolation(MemberSocialAccount.PROVIDER_USER_UNIQUE_CONSTRAINT);
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.NAVER, "naver-social-concurrent"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("소셜충돌")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(memberSocialAccountRepository.saveAndFlush(any(MemberSocialAccount.class)))
                .willThrow(violation);

        assertThatThrownBy(() -> completeSignUp(profile))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
    }

    @Test
    @DisplayName("알 수 없는 회원 무결성 오류는 닉네임 충돌로 오분류하지 않는다")
    void authenticateOrRegister_UnknownIntegrityViolation_PropagatesOriginalException() {
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE,
                        "google-unknown",
                        "unknown@snowthing.org",
                        true,
                        "알수없는오류");
        DataIntegrityViolationException violation = constraintViolation("unknown_constraint");

        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.GOOGLE, "google-unknown"))
                .willReturn(Optional.empty());
        given(memberRepository.findByEmail("unknown@snowthing.org")).willReturn(Optional.empty());
        given(memberRepository.existsByNickname("알수없는오류")).willReturn(false);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(violation);

        assertThatThrownBy(() -> completeSignUp(profile)).isSameAs(violation);
    }

    private static Member memberWithId(long id, String password, MemberStatus status) {
        Member member =
                Member.builder()
                        .publicId("member-" + id)
                        .email("member" + id + "@snowthing.org")
                        .password(password)
                        .nickname("보더" + id)
                        .role(Role.ROLE_USER)
                        .status(status)
                        .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static MemberSocialAccount account(Member member, SocialProvider provider) {
        return MemberSocialAccount.builder()
                .member(member)
                .provider(provider)
                .providerUserId(provider.name().toLowerCase() + "-" + member.getId())
                .providerEmail("social@snowthing.org")
                .build();
    }

    private static final OAuthUserProfile KAKAO_PROFILE =
            new OAuthUserProfile(SocialProvider.KAKAO, " kakao-1 ", "K@Kakao.com", true, "보더");

    @Test
    @DisplayName("연결하면 지금 회원에게 소셜 계정을 저장한다")
    void linkSocialAccount_Success_SavesForCurrentMember() {
        Member member = memberWithId(7L, "encoded", MemberStatus.ACTIVE);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.empty());
        given(memberSocialAccountRepository.existsByMemberIdAndProvider(7L, SocialProvider.KAKAO))
                .willReturn(false);

        socialAuthService.linkSocialAccount(7L, KAKAO_PROFILE);

        ArgumentCaptor<MemberSocialAccount> captor =
                ArgumentCaptor.forClass(MemberSocialAccount.class);
        verify(memberSocialAccountRepository).saveAndFlush(captor.capture());
        MemberSocialAccount saved = captor.getValue();
        assertThat(saved.getMember()).isSameAs(member);
        assertThat(saved.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(saved.getProviderUserId()).isEqualTo("kakao-1");
        assertThat(saved.getProviderEmail()).isEqualTo("k@kakao.com");
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("다른 회원이 쓰는 소셜 계정은 합치지 않고 거부한다")
    void linkSocialAccount_LinkedToAnotherMember_Rejected() {
        Member member = memberWithId(7L, "encoded", MemberStatus.ACTIVE);
        Member other = memberWithId(8L, null, MemberStatus.ACTIVE);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.of(account(other, SocialProvider.KAKAO)));

        assertThatThrownBy(() -> socialAuthService.linkSocialAccount(7L, KAKAO_PROFILE))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_IN_USE);
        verify(memberSocialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("같은 제공자를 두 번 연결할 수 없다")
    void linkSocialAccount_SameProviderTwice_Rejected() {
        Member member = memberWithId(7L, "encoded", MemberStatus.ACTIVE);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.empty());
        given(memberSocialAccountRepository.existsByMemberIdAndProvider(7L, SocialProvider.KAKAO))
                .willReturn(true);

        assertThatThrownBy(() -> socialAuthService.linkSocialAccount(7L, KAKAO_PROFILE))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
        verify(memberSocialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 내 계정에 연결된 소셜 계정을 다시 연결하면 거부한다")
    void linkSocialAccount_AlreadyMine_Rejected() {
        Member member = memberWithId(7L, "encoded", MemberStatus.ACTIVE);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.of(account(member, SocialProvider.KAKAO)));

        assertThatThrownBy(() -> socialAuthService.linkSocialAccount(7L, KAKAO_PROFILE))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
    }

    @Test
    @DisplayName("동시에 다른 회원이 먼저 저장하면 사용 중인 계정으로 거부한다")
    void linkSocialAccount_RaceOnProviderUser_Rejected() {
        Member member = memberWithId(7L, "encoded", MemberStatus.ACTIVE);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(
                        memberSocialAccountRepository.findByProviderAndProviderUserId(
                                SocialProvider.KAKAO, "kakao-1"))
                .willReturn(Optional.empty());
        given(memberSocialAccountRepository.existsByMemberIdAndProvider(7L, SocialProvider.KAKAO))
                .willReturn(false);
        DataIntegrityViolationException violation =
                constraintViolation(MemberSocialAccount.PROVIDER_USER_UNIQUE_CONSTRAINT);
        given(memberSocialAccountRepository.saveAndFlush(any(MemberSocialAccount.class)))
                .willThrow(violation);

        assertThatThrownBy(() -> socialAuthService.linkSocialAccount(7L, KAKAO_PROFILE))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_IN_USE);
    }

    @Test
    @DisplayName("정지된 회원은 소셜 계정을 연결할 수 없다")
    void linkSocialAccount_SuspendedMember_Rejected() {
        Member member = memberWithId(7L, "encoded", MemberStatus.SUSPENDED);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> socialAuthService.linkSocialAccount(7L, KAKAO_PROFILE))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
        verify(memberSocialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("비밀번호가 있는 회원은 하나뿐인 소셜 연결도 해제할 수 있다")
    void unlinkSocialAccount_PasswordRemains_Deletes() {
        Member member = memberWithId(7L, "encoded", MemberStatus.ACTIVE);
        MemberSocialAccount kakao = account(member, SocialProvider.KAKAO);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(memberSocialAccountRepository.findAllByMemberId(7L)).willReturn(List.of(kakao));

        socialAuthService.unlinkSocialAccount(7L, SocialProvider.KAKAO);

        verify(memberSocialAccountRepository).delete(kakao);
    }

    @Test
    @DisplayName("소셜로 가입한 회원도 다른 소셜 연결이 남으면 해제할 수 있다")
    void unlinkSocialAccount_AnotherSocialRemains_Deletes() {
        Member member = memberWithId(7L, null, MemberStatus.ACTIVE);
        MemberSocialAccount kakao = account(member, SocialProvider.KAKAO);
        MemberSocialAccount google = account(member, SocialProvider.GOOGLE);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(memberSocialAccountRepository.findAllByMemberId(7L))
                .willReturn(List.of(kakao, google));

        socialAuthService.unlinkSocialAccount(7L, SocialProvider.KAKAO);

        verify(memberSocialAccountRepository).delete(kakao);
    }

    @Test
    @DisplayName("마지막 남은 로그인 방법은 해제할 수 없다")
    void unlinkSocialAccount_LastLoginMethod_Rejected() {
        Member member = memberWithId(7L, null, MemberStatus.ACTIVE);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(memberSocialAccountRepository.findAllByMemberId(7L))
                .willReturn(List.of(account(member, SocialProvider.KAKAO)));

        assertThatThrownBy(() -> socialAuthService.unlinkSocialAccount(7L, SocialProvider.KAKAO))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_LAST_LOGIN_METHOD);
        verify(memberSocialAccountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("연결되지 않은 제공자는 해제할 수 없다")
    void unlinkSocialAccount_NotLinked_Rejected() {
        Member member = memberWithId(7L, "encoded", MemberStatus.ACTIVE);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(memberSocialAccountRepository.findAllByMemberId(7L)).willReturn(List.of());

        assertThatThrownBy(() -> socialAuthService.unlinkSocialAccount(7L, SocialProvider.NAVER))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED);
    }

    @Test
    @DisplayName("로그인 방법 목록은 비밀번호 여부와 연결된 제공자를 돌려준다")
    void getSocialAccounts_ReturnsPasswordAndProviders() {
        Member member = memberWithId(7L, null, MemberStatus.ACTIVE);
        given(memberRepository.findById(7L)).willReturn(Optional.of(member));
        given(memberSocialAccountRepository.findAllByMemberId(7L))
                .willReturn(List.of(account(member, SocialProvider.KAKAO)));

        var response = socialAuthService.getSocialAccounts(7L);

        assertThat(response.passwordLogin()).isFalse();
        assertThat(response.accounts())
                .extracting(account -> account.provider())
                .containsExactly(SocialProvider.KAKAO);
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        given(cause.getConstraintName()).willReturn(constraintName);
        return new DataIntegrityViolationException("constraint violation", cause);
    }
}
