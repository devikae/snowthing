package com.ikae.snowthing.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.apitoken.entity.ApiTokenScope;
import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.carpool.repository.CarpoolDetailRepository;
import com.ikae.snowthing.domain.carpool.service.CarpoolService;
import com.ikae.snowthing.domain.market.entity.MarketCategory;
import com.ikae.snowthing.domain.market.entity.MarketListing;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.market.repository.MarketListingRepository;
import com.ikae.snowthing.domain.market.service.MarketListingService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.ApiTokenUserDetails;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;

/** 관리자가 카풀, 중고장터 상세 화면에서 바로 가리고 푸는 흐름 (IKA-90). 가린 글은 관리자 로그인 세션에만 보인다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminHiddenDetailIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate tx;
    @Autowired private MemberRepository members;
    @Autowired private PostRepository posts;
    @Autowired private PostCategoryRepository postCategories;
    @Autowired private ResortRepository resorts;
    @Autowired private CarpoolDetailRepository carpoolDetails;
    @Autowired private MarketCategoryRepository marketCategories;
    @Autowired private MarketListingRepository marketListings;
    @Autowired private CarpoolService carpoolService;
    @Autowired private MarketListingService marketListingService;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> postIds = new ArrayList<>();
    private final List<Long> resortIds = new ArrayList<>();
    private Member admin;
    private Member writer;
    private MockHttpSession adminSession;
    private MockHttpSession userSession;

    @BeforeEach
    void setUp() throws Exception {
        admin = member("adm", Role.ROLE_ADMIN);
        writer = member("wrt", Role.ROLE_USER);
        Member user = member("usr", Role.ROLE_USER);
        adminSession = login(admin);
        userSession = login(user);
    }

    @AfterEach
    void tearDown() {
        for (Long postId : postIds) {
            jdbc.update("DELETE FROM carpool_detail WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM market_listing WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
        }
        for (Long resortId : resortIds) {
            jdbc.update("DELETE FROM resort WHERE resort_id = ?", resortId);
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("가린 카풀은 관리자만 상세를 읽고, 일반 회원과 비로그인은 404, 해제하면 다시 보인다")
    void hiddenCarpoolDetailIsAdminOnly() throws Exception {
        String publicId = carpool();
        String url = "/api/v1/carpools/" + publicId;
        mockMvc.perform(get(url).session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));

        mockMvc.perform(blind(publicId, true).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(statusOf(publicId)).isEqualTo("BLOCKED");

        mockMvc.perform(get(url).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        mockMvc.perform(get(url).session(userSession)).andExpect(status().isNotFound());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());

        mockMvc.perform(blind(publicId, false).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url).session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));
    }

    @Test
    @DisplayName("임시조치 카풀은 관리자는 읽고, 일반 회원은 기존처럼 임시조치 안내(403)를 받는다")
    void temporarilyHeldCarpoolKeepsHoldMessageForMembers() throws Exception {
        String publicId = carpool();
        jdbc.update("UPDATE post SET status = 'TEMPORARY_HOLD' WHERE public_id = ?", publicId);
        String url = "/api/v1/carpools/" + publicId;

        mockMvc.perform(get(url).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TEMPORARY_HOLD"));
        mockMvc.perform(get(url).session(userSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_009"));
    }

    @Test
    @DisplayName("숨긴 판매글은 관리자만 상세를 읽고, 일반 회원과 비로그인은 404, 공개로 되돌리면 다시 보인다")
    void hiddenMarketListingDetailIsAdminOnly() throws Exception {
        String publicId = marketListing();
        String url = "/api/v1/market-listings/" + publicId;
        mockMvc.perform(get(url).session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));

        for (String hidden : List.of("HIDDEN", "BLOCKED")) {
            mockMvc.perform(moderate(publicId, hidden).session(adminSession).with(csrf()))
                    .andExpect(status().isNoContent());
            assertThat(statusOf(publicId)).isEqualTo(hidden);

            mockMvc.perform(get(url).session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(hidden))
                    .andExpect(jsonPath("$.canDelete").value(true));
            mockMvc.perform(get(url).session(userSession)).andExpect(status().isNotFound());
            mockMvc.perform(get(url)).andExpect(status().isNotFound());
            mockMvc.perform(get(url + "/share-preview").session(adminSession))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(moderate(publicId, "NORMAL").session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url).session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));
    }

    @Test
    @DisplayName("봇 위임과 API 토큰은 소유자가 관리자여도 가린 카풀과 판매글을 읽지 못한다")
    void delegatedAndApiTokenAdminsCannotReadHiddenDetails() {
        String carpoolId = carpool();
        String listingId = marketListing();
        jdbc.update(
                "UPDATE post SET status = 'BLOCKED' WHERE public_id IN (?, ?)",
                carpoolId,
                listingId);
        BotDelegatedUserDetails delegated = new BotDelegatedUserDetails(admin);
        ApiTokenUserDetails apiToken =
                new ApiTokenUserDetails(admin, 1L, Set.of(ApiTokenScope.READ));

        assertThatThrownBy(() -> carpoolService.find(carpoolId, delegated))
                .hasMessageContaining("존재하지 않거나 삭제된 게시글");
        assertThatThrownBy(() -> carpoolService.find(carpoolId, apiToken))
                .hasMessageContaining("존재하지 않거나 삭제된 게시글");
        assertThatThrownBy(() -> marketListingService.getDetail(listingId, delegated, false))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("조회할 수 없는 중고장터 판매글");
        assertThatThrownBy(() -> marketListingService.getDetail(listingId, apiToken, false))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("조회할 수 없는 중고장터 판매글");
    }

    private MockHttpServletRequestBuilder blind(String publicId, boolean blinded) {
        return patch("/api/v1/admin/posts/" + publicId + "/blind")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blinded\":" + blinded + "}");
    }

    private MockHttpServletRequestBuilder moderate(String publicId, String status) {
        return patch("/api/v1/admin/market-listings/" + publicId + "/moderation-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"moderationStatus\":\"" + status + "\"}");
    }

    private String statusOf(String publicId) {
        return jdbc.queryForObject(
                "SELECT status FROM post WHERE public_id = ?", String.class, publicId);
    }

    private String carpool() {
        return tx.execute(
                status -> {
                    String suffix = UUID.randomUUID().toString().substring(0, 8);
                    PostCategory category =
                            postCategories
                                    .findByCode("CARPOOL")
                                    .orElseGet(
                                            () ->
                                                    postCategories.save(
                                                            PostCategory.builder()
                                                                    .name("카풀")
                                                                    .code("CARPOOL")
                                                                    .build()));
                    Resort resort =
                            resorts.save(
                                    Resort.builder()
                                            .name("가리기리조트" + suffix)
                                            .regionName("강원")
                                            .code("H" + suffix)
                                            .displayOrder(1)
                                            .build());
                    resortIds.add(resort.getId());
                    Post saved = savePost(category, "가리기 카풀 " + suffix);
                    carpoolDetails.save(
                            CarpoolDetail.builder()
                                    .post(saved)
                                    // 커밋되는 데이터라 다른 카풀 검색 테스트에 걸리지 않는 값과 지난 시각을 쓴다.
                                    .departureRegion("가리기출발")
                                    .meetingPlace("가리기집결")
                                    .destinationResort(resort)
                                    .destinationLatitude(BigDecimal.valueOf(37.5))
                                    .destinationLongitude(BigDecimal.valueOf(127.0))
                                    .tripType(CarpoolTripType.ONE_WAY)
                                    .departureAt(
                                            LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                                                    .minusDays(1))
                                    .passengerCapacity(1)
                                    .costMode(CarpoolCostMode.MANUAL)
                                    .estimatedCostPerPerson(10000)
                                    .build());
                    return saved.getPublicId();
                });
    }

    private String marketListing() {
        return tx.execute(
                status -> {
                    PostCategory postCategory =
                            postCategories
                                    .findByCode("MARKET")
                                    .orElseGet(
                                            () ->
                                                    postCategories.save(
                                                            PostCategory.builder()
                                                                    .code("MARKET")
                                                                    .name("Market")
                                                                    .build()));
                    MarketCategory category =
                            marketCategories
                                    .findByCode("SNOWBOARD")
                                    .orElseGet(
                                            () ->
                                                    marketCategories.save(
                                                            MarketCategory.builder()
                                                                    .code("SNOWBOARD")
                                                                    .name("Snowboard")
                                                                    .active(true)
                                                                    .build()));
                    Post saved = savePost(postCategory, "가리기 판매글");
                    marketListings.save(
                            MarketListing.builder()
                                    .post(saved)
                                    .category(category)
                                    .productCondition(ProductCondition.GOOD)
                                    .transactionMethod(TransactionMethod.DIRECT)
                                    .price(10000L)
                                    .contact("test-contact")
                                    .build());
                    return saved.getPublicId();
                });
    }

    private Post savePost(PostCategory category, String title) {
        Post saved =
                posts.save(
                        Post.builder()
                                .member(writer)
                                .category(category)
                                .title(title)
                                .content("본문")
                                .writerIp("192.0.2.1")
                                .isAnonymous(false)
                                .build());
        postIds.add(saved.getId());
        return saved;
    }

    private MockHttpSession login(Member member) throws Exception {
        MvcResult result =
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
                        .andExpect(status().isOk())
                        .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Member member(String prefix, Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member saved =
                members.save(
                        Member.builder()
                                .email(prefix + "-" + unique + "@snowthing.com")
                                .password(passwordEncoder.encode(PASSWORD))
                                .nickname(prefix + unique)
                                .role(role)
                                .status(MemberStatus.ACTIVE)
                                .build());
        memberIds.add(saved.getId());
        return saved;
    }
}
