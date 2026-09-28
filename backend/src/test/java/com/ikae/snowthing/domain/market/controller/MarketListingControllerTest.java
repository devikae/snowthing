package com.ikae.snowthing.domain.market.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.market.dto.MarketListingCreateRequest;
import com.ikae.snowthing.domain.market.entity.MarketCategory;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarketListingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository postCategoryRepository;
    @Autowired private MarketCategoryRepository marketCategoryRepository;

    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        postCategoryRepository
                .findByCode("MARKET")
                .orElseGet(
                        () ->
                                postCategoryRepository.save(
                                        PostCategory.builder()
                                                .code("MARKET")
                                                .name("중고장터")
                                                .build()));
        marketCategoryRepository
                .findByCode("SNOWBOARD")
                .orElseGet(
                        () ->
                                marketCategoryRepository.save(
                                        MarketCategory.builder()
                                                .code("SNOWBOARD")
                                                .name("스노보드")
                                                .sortOrder(1)
                                                .active(true)
                                                .build()));
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email("market-controller@test.com")
                                .nickname("장터판매자")
                                .role(Role.ROLE_USER)
                                .build());
        userDetails = new CustomUserDetails(member);
    }

    @Test
    void 공개_미리보기는_비로그인도_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/market-listings/preview").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void 중고장터_목록은_비로그인_요청을_거부한다() throws Exception {
        mockMvc.perform(get("/api/v1/market-listings").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 판매글_등록은_CSRF_토큰이_없으면_거부한다() throws Exception {
        MarketListingCreateRequest request = validRequest();

        mockMvc.perform(
                        post("/api/v1/market-listings")
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 회원은_판매글을_등록하고_상세를_조회할_수_있다() throws Exception {
        MarketListingCreateRequest request = validRequest();

        String response =
                mockMvc.perform(
                                post("/api/v1/market-listings")
                                        .with(csrf())
                                        .with(user(userDetails))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.tradeStatus").value("ON_SALE"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String publicId = objectMapper.readTree(response).get("publicId").asText();

        mockMvc.perform(get("/api/v1/market-listings/{publicId}", publicId).with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.title").value("데크 판매합니다"))
                .andExpect(jsonPath("$.contact").value("010-1234-5678"))
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void 일반_게시글_상세와_추천_API로_판매글_정책을_우회할_수_없다() throws Exception {
        String publicId = createListing();

        mockMvc.perform(get("/api/v1/posts/{publicId}", publicId).with(user(userDetails)))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        put("/api/v1/posts/{publicId}/reaction", publicId)
                                .queryParam("type", "LIKE")
                                .with(csrf())
                                .with(user(userDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MARKET_007"));
    }

    @Test
    void 판매글_댓글은_비로그인과_익명_작성을_거부한다() throws Exception {
        String publicId = createListing();

        mockMvc.perform(get("/api/v1/posts/{publicId}/comments", publicId).with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/v1/posts/{publicId}/comments", publicId)
                                .with(csrf())
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "content": "익명 댓글",
                                          "isAnonymous": true
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MARKET_008"));
    }

    @Test
    void 공개_미리보기는_최소_필드만_노출한다() throws Exception {
        createListing();

        mockMvc.perform(get("/api/v1/market-listings/preview").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].publicId").isString())
                .andExpect(jsonPath("$.items[0].title").doesNotExist())
                .andExpect(jsonPath("$.items[0].price").doesNotExist())
                .andExpect(jsonPath("$.items[0].contact").doesNotExist())
                .andExpect(jsonPath("$.items[0].seller").doesNotExist());
    }

    @Test
    void 이천만원을_넘는_가격은_거부한다() throws Exception {
        MarketListingCreateRequest request =
                new MarketListingCreateRequest(
                        "SNOWBOARD",
                        "비정상 가격",
                        "본문",
                        ProductCondition.GOOD,
                        TransactionMethod.DIRECT,
                        20_000_001L,
                        false,
                        false,
                        "contact",
                        List.of());

        mockMvc.perform(
                        post("/api/v1/market-listings")
                                .with(csrf())
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MARKET_004"));
    }

    private String createListing() throws Exception {
        String response =
                mockMvc.perform(
                                post("/api/v1/market-listings")
                                        .with(csrf())
                                        .with(user(userDetails))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).get("publicId").asText();
    }

    private MarketListingCreateRequest validRequest() {
        return new MarketListingCreateRequest(
                "SNOWBOARD",
                "데크 판매합니다",
                "사용감이 조금 있습니다.",
                ProductCondition.GOOD,
                TransactionMethod.BOTH,
                350_000L,
                true,
                false,
                "010-1234-5678",
                List.of());
    }
}
