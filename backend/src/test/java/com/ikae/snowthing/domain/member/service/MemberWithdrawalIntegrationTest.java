package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.auth.service.SocialAuthService;
import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.carpool.repository.CarpoolDetailRepository;
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.email.security.EmailVerificationHasher;
import com.ikae.snowthing.domain.email.service.EmailVerificationService;
import com.ikae.snowthing.domain.image.service.ImageService;
import com.ikae.snowthing.domain.market.dto.MarketListingCreateRequest;
import com.ikae.snowthing.domain.market.entity.MarketCategory;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.market.service.MarketListingService;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberWithdrawalRequest;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberSocialAccount;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberSocialAccountRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.member.repository.WithdrawnEmailRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostScrapService;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 실제 MySQL과 보안 필터로 탈퇴 전 과정을 본다. 테스트 DB에 없는 요청 한도 테이블을 쓰는 제한기만 대체한다. */
@SpringBootTest
@AutoConfigureMockMvc
class MemberWithdrawalIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String WITHDRAWAL_URL = "/api/v1/members/me/withdrawal";
    private static final String IP = "203.0.113.77";

    @MockitoBean private ProfileActionRateLimiter rateLimiter;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberSocialAccountRepository socialAccountRepository;
    @Autowired private WithdrawnEmailRepository withdrawnEmailRepository;
    @Autowired private WithdrawnEmailCleanupScheduler cleanupScheduler;
    @Autowired private MemberWithdrawalService withdrawalService;
    @Autowired private SocialAuthService socialAuthService;
    @Autowired private EmailVerificationService emailVerificationService;
    @Autowired private MemberService memberService;
    @Autowired private EmailVerificationHasher hasher;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PostService postService;
    @Autowired private PostScrapService postScrapService;
    @Autowired private CommentService commentService;
    @Autowired private MarketListingService marketListingService;
    @Autowired private MarketCategoryRepository marketCategoryRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CarpoolDetailRepository carpoolDetailRepository;
    @Autowired private ResortRepository resortRepository;
    @Autowired private ImageService imageService;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> memberIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        for (String code : List.of("FREE", "MARKET", "CARPOOL")) {
            categoryRepository
                    .findByCode(code)
                    .orElseGet(() -> categoryRepository.save(new PostCategory(code, code)));
        }
        if (marketCategoryRepository.findByCode("SNOWBOARD").isEmpty()) {
            marketCategoryRepository.save(
                    MarketCategory.builder().code("SNOWBOARD").name("보드").active(true).build());
        }
    }

    @AfterEach
    void tearDown() {
        for (Long id : memberIds) {
            jdbc.update(
                    "DELETE FROM carpool_detail WHERE post_id IN"
                            + " (SELECT post_id FROM post WHERE member_id = ?)",
                    id);
            jdbc.update(
                    "DELETE FROM market_listing WHERE post_id IN"
                            + " (SELECT post_id FROM post WHERE member_id = ?)",
                    id);
            jdbc.update("DELETE FROM notification WHERE receiver_id = ?", id);
            jdbc.update(
                    "DELETE FROM post_scrap WHERE member_id = ? OR post_id IN"
                            + " (SELECT post_id FROM post WHERE member_id = ?)",
                    id,
                    id);
            jdbc.update("DELETE FROM member_block WHERE blocker_id = ? OR blocked_id = ?", id, id);
            jdbc.update("DELETE FROM api_token WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member_social_account WHERE member_id = ?", id);
            jdbc.update(
                    "DELETE FROM post_reaction WHERE post_id IN"
                            + " (SELECT post_id FROM post WHERE member_id = ?)",
                    id);
            jdbc.update(
                    "DELETE c FROM comment c JOIN post p ON p.post_id = c.post_id"
                            + " WHERE p.member_id = ? AND c.parent_id IS NOT NULL",
                    id);
            jdbc.update(
                    "DELETE c FROM comment c JOIN post p ON p.post_id = c.post_id"
                            + " WHERE p.member_id = ?",
                    id);
            jdbc.update("DELETE FROM comment WHERE member_id = ?", id);
            jdbc.update(
                    "DELETE r FROM post_content_image_reference r"
                            + " JOIN post p ON p.post_id = r.post_id WHERE p.member_id = ?",
                    id);
            jdbc.update("DELETE FROM post WHERE member_id = ?", id);
            jdbc.update("DELETE FROM member WHERE member_id = ?", id);
        }
        jdbc.update("DELETE FROM withdrawn_email");
        jdbc.update("DELETE FROM profile_image_cleanup");
    }

    @Test
    @DisplayName("비밀번호 탈퇴는 개인정보를 지우고 글과 댓글은 탈퇴한 회원으로 남긴다")
    void passwordWithdrawalAnonymizesAndKeepsContent() throws Exception {
        String imageKey = "public/profiles/" + UUID.randomUUID() + ".jpg";
        Member member = saveMember("wd", PASSWORD, MemberStatus.ACTIVE, imageKey);
        Member other = saveMember("other", PASSWORD, MemberStatus.ACTIVE, null);
        CustomUserDetails principal = new CustomUserDetails(member);
        String originalEmail = member.getEmail();
        String originalNickname = member.getNickname();

        String postId =
                postService
                        .createPost(
                                new PostCreateRequest("FREE", "남는 글", "본문", false, null, List.of()),
                                principal,
                                IP)
                        .publicId();
        String otherPostId =
                postService
                        .createPost(
                                new PostCreateRequest("FREE", "남의 글", "본문", false, null, List.of()),
                                new CustomUserDetails(other),
                                IP)
                        .publicId();
        commentService.createComment(
                postId, new CommentCreateRequest(null, "내 글 댓글", false, null), principal, IP);
        commentService.createComment(
                otherPostId, new CommentCreateRequest(null, "남의 글 댓글", false, null), principal, IP);
        String listingId =
                marketListingService
                        .create(
                                new MarketListingCreateRequest(
                                        "SNOWBOARD",
                                        "판매",
                                        "본문",
                                        ProductCondition.GOOD,
                                        TransactionMethod.DIRECT,
                                        10000L,
                                        false,
                                        false,
                                        "010-1234-5678",
                                        List.of()),
                                principal,
                                IP)
                        .publicId();
        Long carpoolPostId = saveCarpool(member);
        postScrapService.scrap(otherPostId, principal);
        jdbc.update(
                "INSERT INTO member_registration_consent"
                        + " (member_id, privacy_version, age_confirmed, marketing_email_agreed,"
                        + " confirmed_at) VALUES (?, 'v1', TRUE, TRUE, NOW())",
                member.getId());
        jdbc.update(
                "INSERT INTO member_marketing_consent_history (member_id, agreed, changed_at)"
                        + " VALUES (?, TRUE, NOW())",
                member.getId());
        jdbc.update(
                "INSERT INTO post_image_upload (image_key, owner_email, state, due_at)"
                        + " VALUES (?, ?, 'PENDING', NOW())",
                "public/posts/" + UUID.randomUUID() + ".jpg",
                originalEmail);
        jdbc.update(
                "INSERT INTO notification (receiver_id, type, post_id, post_public_id,"
                        + " post_category_code, comment_id, actor_name, preview, is_read,"
                        + " created_at) VALUES (?, 'POST_COMMENT', 1, ?, 'FREE', 1, ?, 'x', FALSE,"
                        + " NOW())",
                other.getId(),
                otherPostId,
                originalNickname);

        MockHttpSession session = login(originalEmail, PASSWORD);
        mockMvc.perform(withdrawal(session, new MemberWithdrawalRequest(PASSWORD, null)))
                .andExpect(status().isNoContent());

        Member after = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(after.getEmail())
                .isEqualTo("withdrawn-" + member.getId() + "@withdrawn.invalid");
        assertThat(after.getNickname()).isEqualTo("withdrawn_" + member.getId());
        assertThat(after.getPassword()).isNull();
        assertThat(after.getProfileImageUrl()).isNull();
        assertThat(after.getBio()).isNull();
        assertThat(after.getDepartureRegion()).isNull();
        assertThat(after.getEmailVerifiedAt()).isNull();

        assertThat(count("member_registration_consent", member.getId())).isZero();
        assertThat(count("member_marketing_consent_history", member.getId())).isZero();
        assertThat(count("post_scrap", member.getId())).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM post_image_upload WHERE owner_email = ?",
                                Long.class,
                                originalEmail))
                .isZero();
        assertThat(
                        jdbc.queryForList(
                                "SELECT actor_name FROM notification WHERE receiver_id = ?",
                                String.class,
                                other.getId()))
                .isNotEmpty()
                .allMatch(Member.WITHDRAWN_NICKNAME::equals);
        // 프로필 사진은 기존 cleanup 작업으로 넘어간다.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM profile_image_cleanup WHERE image_key = ?",
                                Long.class,
                                imageKey))
                .isEqualTo(1L);
        // 해시만 남고 원문 이메일은 어디에도 없다.
        Map<String, Object> hash =
                jdbc.queryForMap(
                        "SELECT email_hash, withdrawn_at, expires_at FROM withdrawn_email"
                                + " WHERE email_hash = ?",
                        hasher.digestWithdrawnEmail(originalEmail));
        assertThat(hash.get("email_hash").toString()).hasSize(64).doesNotContain("@");
        // IP는 앞 두 자리만 남긴다.
        assertThat(
                        jdbc.queryForList(
                                "SELECT writer_ip FROM post WHERE member_id = ?",
                                String.class,
                                member.getId()))
                .isNotEmpty()
                .allMatch("203.0.0.0"::equals);
        assertThat(
                        jdbc.queryForList(
                                "SELECT writer_ip FROM comment WHERE member_id = ?",
                                String.class,
                                member.getId()))
                .hasSize(2)
                .allMatch("203.0.0.0"::equals);
        // 연락처는 지우고 판매글은 닫는다.
        assertThat(
                        jdbc.queryForMap(
                                "SELECT l.contact, l.trade_status FROM market_listing l"
                                        + " JOIN post p ON p.post_id = l.post_id"
                                        + " WHERE p.public_id = ?",
                                listingId))
                .containsEntry("contact", Member.WITHDRAWN_NICKNAME)
                .containsEntry("trade_status", "SOLD");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT contact_info FROM carpool_detail WHERE post_id = ?",
                                String.class,
                                carpoolPostId))
                .isNull();

        // 글과 댓글은 그대로 보이고 작성자만 탈퇴한 회원으로 보인다.
        mockMvc.perform(get("/api/v1/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("남는 글"))
                .andExpect(jsonPath("$.writer.nickname").value(Member.WITHDRAWN_NICKNAME))
                .andExpect(jsonPath("$.writer.publicId").doesNotExist());
        mockMvc.perform(get("/api/v1/posts/" + otherPostId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].content").value("남의 글 댓글"))
                .andExpect(jsonPath("$.comments[0].writer").doesNotExist());
        String carpoolPublicId =
                jdbc.queryForObject(
                        "SELECT public_id FROM post WHERE post_id = ?",
                        String.class,
                        carpoolPostId);
        mockMvc.perform(get("/api/v1/carpools/" + carpoolPublicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writerName").value(Member.WITHDRAWN_NICKNAME))
                .andExpect(jsonPath("$.writerPublicId").doesNotExist())
                .andExpect(jsonPath("$.contactInfo").doesNotExist());
        // 작성자 검색으로 익명화한 닉네임이 찾히지 않는다.
        mockMvc.perform(
                        get("/api/v1/posts")
                                .param("categoryCode", "FREE")
                                .param("searchType", "WRITER")
                                .param("keyword", "withdrawn_" + member.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        // 닉네임이나 프로필이 응답 어디에도 남지 않는다.
        String market =
                mockMvc.perform(
                                get("/api/v1/market-listings/" + listingId)
                                        .session(login(other.getEmail(), PASSWORD)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.seller.nickname").value(Member.WITHDRAWN_NICKNAME))
                        .andExpect(jsonPath("$.seller.publicId").doesNotExist())
                        .andExpect(jsonPath("$.contact").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(market).doesNotContain(originalNickname).doesNotContain("010-1234-5678");
    }

    @Test
    @DisplayName("틀린 비밀번호는 거부하고 회원과 세션을 그대로 둔다")
    void wrongPasswordChangesNothing() throws Exception {
        Member member = saveMember("wrong", PASSWORD, MemberStatus.ACTIVE, null);
        MockHttpSession session = login(member.getEmail(), PASSWORD);

        mockMvc.perform(withdrawal(session, new MemberWithdrawalRequest("different1!", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CURRENT_PASSWORD_MISMATCH"));
        mockMvc.perform(withdrawal(session, new MemberWithdrawalRequest(null, "탈퇴")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CURRENT_PASSWORD_MISMATCH"));

        Member after = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(after.getEmail()).isEqualTo(member.getEmail());
        assertThat(session.isInvalid()).isFalse();
        assertThat(
                        withdrawnEmailRepository.existsById(
                                hasher.digestWithdrawnEmail(after.getEmail())))
                .isFalse();
    }

    private com.ikae.snowthing.domain.auth.dto.SocialSignUpRequest socialConsent() {
        var policy = new com.ikae.snowthing.domain.member.service.RegistrationPrivacyPolicy();
        return com.ikae.snowthing.domain.auth.dto.SocialSignUpRequest.builder()
                .ageConfirmed(true)
                .privacyAgreed(true)
                .termsAgreed(true)
                .marketingEmailAgreed(false)
                .privacyVersion(policy.version())
                .termsVersion(policy.termsVersion())
                .build();
    }

    @Test
    @DisplayName("소셜 회원은 확인 문구로 탈퇴하고 같은 구글, 네이버 계정의 재가입이 막힌다")
    void socialWithdrawalBlocksSocialRejoin() {
        String sub = UUID.randomUUID().toString();
        String email = "g-" + sub.substring(0, 8) + "@example.com";
        Member google =
                track(
                        socialAuthService.completeSignUp(
                                new OAuthUserProfile(
                                        SocialProvider.GOOGLE, sub, email, true, "구글회원"),
                                socialConsent()));
        String naverId = UUID.randomUUID().toString();
        String naverEmail = "n-" + naverId.substring(0, 8) + "@example.com";
        Member naver =
                track(
                        socialAuthService.completeSignUp(
                                new OAuthUserProfile(
                                        SocialProvider.NAVER, naverId, naverEmail, false, "네이버회원"),
                                socialConsent()));

        assertThatThrownBy(
                        () ->
                                withdrawalService.withdraw(
                                        google.getId(), new MemberWithdrawalRequest(null, "탈퇴해")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWAL_CONFIRMATION_MISMATCH);
        assertThat(socialAccountRepository.findAllByMemberId(google.getId())).hasSize(1);

        withdrawalService.withdraw(google.getId(), new MemberWithdrawalRequest(null, "탈퇴"));
        withdrawalService.withdraw(naver.getId(), new MemberWithdrawalRequest(null, "탈퇴"));

        List<MemberSocialAccount> left = socialAccountRepository.findAllByMemberId(google.getId());
        assertThat(left).isEmpty();
        assertThatThrownBy(
                        () ->
                                socialAuthService.findMemberForLogin(
                                        new OAuthUserProfile(
                                                SocialProvider.GOOGLE, sub, email, true, "구글회원")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWN_EMAIL_RESTRICTED);
        assertThatThrownBy(
                        () ->
                                socialAuthService.completeSignUp(
                                        new OAuthUserProfile(
                                                SocialProvider.NAVER,
                                                naverId,
                                                naverEmail,
                                                false,
                                                "네이버회원"),
                                        socialConsent()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWN_EMAIL_RESTRICTED);
        // 네이버 회원의 제공자 이메일로 하는 일반 가입도 막힌다.
        assertThat(emailVerificationService.isEmailAvailable(naverEmail)).isFalse();
    }

    @Test
    @DisplayName("정지 회원은 탈퇴할 수 없다")
    void suspendedCannotWithdraw() {
        Member member = saveMember("sus", PASSWORD, MemberStatus.SUSPENDED, null);

        assertThatThrownBy(
                        () ->
                                withdrawalService.withdraw(
                                        member.getId(),
                                        new MemberWithdrawalRequest(PASSWORD, null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        Member after = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(after.getEmail()).isEqualTo(member.getEmail());
    }

    @Test
    @DisplayName("비밀번호도 소셜 연결도 없는 계정(AI 멤버)은 탈퇴할 수 없다")
    void accountWithoutCredentialsCannotWithdraw() {
        Member member = saveMember("ai", null, MemberStatus.ACTIVE, null);

        assertThatThrownBy(
                        () ->
                                withdrawalService.withdraw(
                                        member.getId(), new MemberWithdrawalRequest(null, "탈퇴")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("탈퇴하면 모든 기기의 세션이 끊기고 다시 로그인할 수 없으며 API 토큰도 폐기된다")
    void withdrawalInvalidatesEverySessionAndToken() throws Exception {
        Member member = saveMember("sess", PASSWORD, MemberStatus.ACTIVE, null);
        jdbc.update(
                "INSERT INTO api_token (public_id, member_id, name, token_prefix, token_hash,"
                        + " scopes, expires_at, created_at) VALUES (?, ?, 'bot', 'snt_abcd', ?,"
                        + " 'READ', DATE_ADD(NOW(), INTERVAL 1 DAY), NOW())",
                UUID.randomUUID().toString(),
                member.getId(),
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""));
        MockHttpSession first = login(member.getEmail(), PASSWORD);
        MockHttpSession second = login(member.getEmail(), PASSWORD);

        mockMvc.perform(get("/api/v1/members/me").session(second)).andExpect(status().isOk());
        mockMvc.perform(withdrawal(first, new MemberWithdrawalRequest(PASSWORD, null)))
                .andExpect(status().isNoContent());

        assertThat(first.isInvalid()).isTrue();
        assertThat(second.isInvalid()).isTrue();
        mockMvc.perform(get("/api/v1/members/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "email",
                                                        member.getEmail(),
                                                        "password",
                                                        PASSWORD))))
                .andExpect(status().is4xxClientError());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM api_token WHERE member_id = ?"
                                        + " AND revoked_at IS NULL",
                                Long.class,
                                member.getId()))
                .isZero();
    }

    @Test
    @DisplayName("같은 이메일 재가입은 막히고 30일 뒤 풀리며 스케줄러가 해시를 지운다")
    void rejoinBlockedThenReleasedAfterThirtyDays() {
        Member member = saveMember("rejoin", PASSWORD, MemberStatus.ACTIVE, null);
        String email = member.getEmail();
        String upperEmail = email.toUpperCase(java.util.Locale.ROOT);

        withdrawalService.withdraw(member.getId(), new MemberWithdrawalRequest(PASSWORD, null));

        assertThat(emailVerificationService.isEmailAvailable(upperEmail)).isFalse();
        assertThatThrownBy(() -> emailVerificationService.requestSignUp(email, "127.0.0.1"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWN_EMAIL_RESTRICTED);
        // 인증을 먼저 끝낸 뒤 탈퇴한 경우에도 가입 저장 단계에서 막힌다.
        assertThatThrownBy(
                        () ->
                                memberService.signUp(
                                        MemberSignUpRequest.builder()
                                                .email(email)
                                                .password(PASSWORD)
                                                .nickname("재가입" + member.getId())
                                                .build()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWN_EMAIL_RESTRICTED);

        LocalDateTime withdrawnAt =
                withdrawnEmailRepository
                        .findById(hasher.digestWithdrawnEmail(email))
                        .orElseThrow()
                        .getWithdrawnAt();
        assertThat(blockedAt(email, withdrawnAt.plusDays(30).minusSeconds(1))).isTrue();
        assertThat(blockedAt(email, withdrawnAt.plusDays(30))).isFalse();

        assertThat(cleanupScheduler.deleteExpiredAt(withdrawnAt.plusDays(29))).isZero();
        assertThat(withdrawnEmailRepository.existsById(hasher.digestWithdrawnEmail(email)))
                .isTrue();
        assertThat(cleanupScheduler.deleteExpiredAt(withdrawnAt.plusDays(30))).isPositive();
        assertThat(withdrawnEmailRepository.existsById(hasher.digestWithdrawnEmail(email)))
                .isFalse();
        assertThat(emailVerificationService.isEmailAvailable(email)).isTrue();
    }

    @Test
    @DisplayName("동시에 두 번 탈퇴하면 한 번만 성공하고 나머지는 이미 탈퇴로 끝난다")
    void concurrentWithdrawalSucceedsOnce() throws Exception {
        Member member = saveMember("race", PASSWORD, MemberStatus.ACTIVE, null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        return withdrawalService.withdraw(
                                                member.getId(),
                                                new MemberWithdrawalRequest(PASSWORD, null));
                                    } catch (CustomException e) {
                                        return e.getErrorCode();
                                    }
                                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get());
            }
            assertThat(outcomes)
                    .containsExactlyInAnyOrder(member.getPublicId(), ErrorCode.ACCOUNT_WITHDRAWN);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean blockedAt(String email, LocalDateTime at) {
        Clock clock =
                Clock.fixed(
                        at.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        return new WithdrawnEmailService(
                        withdrawnEmailRepository, hasher, new EmailNormalizer(), clock)
                .isBlocked(email);
    }

    private Long saveCarpool(Member member) {
        PostCategory category = categoryRepository.findByCode("CARPOOL").orElseThrow();
        Resort resort =
                resortRepository.findAll().stream()
                        .findFirst()
                        .orElseGet(
                                () ->
                                        resortRepository.save(
                                                Resort.builder()
                                                        .name("리조트-" + UUID.randomUUID())
                                                        .regionName("강원")
                                                        .code(
                                                                "R"
                                                                        + UUID.randomUUID()
                                                                                .toString()
                                                                                .substring(0, 8))
                                                        .build()));
        Post carpool =
                postRepository.save(
                        Post.builder()
                                .member(member)
                                .category(category)
                                .title("카풀")
                                .content("본문")
                                .writerIp(IP)
                                .isAnonymous(false)
                                .build());
        carpoolDetailRepository.saveAndFlush(
                CarpoolDetail.builder()
                        .post(carpool)
                        .departureRegion("서울")
                        .meetingPlace("잠실")
                        .destinationResort(resort)
                        .destinationLatitude(BigDecimal.valueOf(37.5))
                        .destinationLongitude(BigDecimal.valueOf(127.0))
                        .tripType(CarpoolTripType.ONE_WAY)
                        .departureAt(LocalDateTime.now().plusDays(3))
                        .passengerCapacity(2)
                        .costMode(CarpoolCostMode.MANUAL)
                        .estimatedCostPerPerson(10000)
                        .contactInfo("010-1234-5678")
                        .build());
        return carpool.getId();
    }

    private long count(String table, Long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE member_id = ?", Long.class, memberId);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withdrawal(
            MockHttpSession session, MemberWithdrawalRequest request) throws Exception {
        return post(WITHDRAWAL_URL)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession)
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                email,
                                                                "password",
                                                                password))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getRequest()
                        .getSession(false);
    }

    private Member track(Member member) {
        memberIds.add(member.getId());
        return member;
    }

    private Member saveMember(
            String label, String password, MemberStatus status, String profileImageKey) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return track(
                memberRepository.save(
                        Member.builder()
                                .email(label + "-" + suffix + "@example.com")
                                .password(
                                        password == null ? null : passwordEncoder.encode(password))
                                .nickname(label + suffix)
                                .bio("소개")
                                .departureRegion("서울")
                                .profileImageUrl(
                                        profileImageKey == null
                                                ? null
                                                : imageService.profileUrl(profileImageKey))
                                .emailVerifiedAt(LocalDateTime.now())
                                .role(Role.ROLE_USER)
                                .status(status)
                                .build()));
    }
}
