package com.ikae.snowthing.domain.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.market.entity.MarketCategory;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 중고장터의 소유자 검사, 거래 상태 전이, 운영 상태, 목록 제한 (IKA-118).
 *
 * <p>기준 문서: docs/conception/002_used_market/04_api-spec.md, 05_access-and-image-policy.md. 작성자만 수정과
 * 거래 상태 변경을 하고, 작성자와 관리자만 삭제하며, 관리자는 운영 상태만 바꾼다. 없는 글과 일반 회원에게 안 보이는 글은 404, 남의 글 수정과 삭제는 403, 버전
 * 불일치는 409다.
 *
 * <p>테스트 트랜잭션 안에서 돌고 끝나면 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarketListingOwnershipIntegrationTest {

    private static final String LISTINGS = "/api/v1/market-listings";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository postCategoryRepository;
    @Autowired private MarketCategoryRepository marketCategoryRepository;

    private Member seller;
    private Member other;
    private Member admin;
    private String publicId;

    @BeforeEach
    void setUp() throws Exception {
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
        String unique = UUID.randomUUID().toString();
        seller = saveMember(unique, "s", Role.ROLE_USER);
        other = saveMember(unique, "o", Role.ROLE_USER);
        admin = saveMember(unique, "a", Role.ROLE_ADMIN);
        publicId = createListing(seller, "데크 판매합니다");
        clear();
    }

    @Nested
    @DisplayName("소유자 검사")
    class Ownership {

        @Test
        @DisplayName("다른 회원과 관리자는 남의 판매글을 수정할 수 없고(403) 내용이 그대로다")
        void onlySellerCanUpdate() throws Exception {
            for (Member member : List.of(other, admin)) {
                update(member, publicId, listingBody("가로챈 제목", 1L, 0L))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("AUTH_002"));
                clear();
            }

            assertThat(title()).isEqualTo("데크 판매합니다");
            assertThat(price()).isEqualTo(350_000L);

            update(seller, publicId, listingBody("가격 내립니다", 300_000L, 0L))
                    .andExpect(status().isOk());
            clear();
            assertThat(title()).isEqualTo("가격 내립니다");
            assertThat(price()).isEqualTo(300_000L);
        }

        @Test
        @DisplayName("다른 회원과 관리자는 남의 판매글의 거래 상태를 바꿀 수 없다(403)")
        void onlySellerCanChangeTradeStatus() throws Exception {
            for (Member member : List.of(other, admin)) {
                tradeStatus(member, publicId, "SOLD", 0)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("AUTH_002"));
                clear();
            }

            assertThat(tradeStatusOf()).isEqualTo("ON_SALE");
        }

        @Test
        @DisplayName("다른 회원의 삭제는 403이고 글이 남는다. 관리자는 삭제할 수 있다")
        void sellerOrAdminCanDelete() throws Exception {
            mockMvc.perform(
                            delete(LISTINGS + "/{id}", publicId)
                                    .with(csrf())
                                    .with(user(details(other))))
                    .andExpect(status().isForbidden());
            clear();
            assertThat(postStatus()).isEqualTo("NORMAL");

            mockMvc.perform(
                            delete(LISTINGS + "/{id}", publicId)
                                    .with(csrf())
                                    .with(user(details(admin))))
                    .andExpect(status().isNoContent());
            clear();
            assertThat(postStatus()).isEqualTo("DELETED");
            mockMvc.perform(get(LISTINGS + "/{id}", publicId).with(user(details(seller))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MARKET_001"));
        }

        @Test
        @DisplayName("비로그인의 등록, 수정, 거래 상태 변경, 삭제는 401이다")
        void anonymousCannotWrite() throws Exception {
            String body = write(listingBody("제목", 1000L, 0L));

            mockMvc.perform(
                            post(LISTINGS)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(
                            put(LISTINGS + "/{id}", publicId)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(
                            patch(LISTINGS + "/{id}/trade-status", publicId)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"tradeStatus\":\"SOLD\",\"version\":0}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete(LISTINGS + "/{id}", publicId).with(csrf()))
                    .andExpect(status().isUnauthorized());

            assertThat(postStatus()).isEqualTo("NORMAL");
            assertThat(tradeStatusOf()).isEqualTo("ON_SALE");
        }

        @Test
        @DisplayName("없는 판매글의 수정, 거래 상태 변경, 삭제는 404다")
        void unknownListingIsNotFound() throws Exception {
            String unknown = UUID.randomUUID().toString();

            update(seller, unknown, listingBody("제목", 1000L, 0L)).andExpect(status().isNotFound());
            tradeStatus(seller, unknown, "SOLD", 0).andExpect(status().isNotFound());
            mockMvc.perform(
                            delete(LISTINGS + "/{id}", unknown)
                                    .with(csrf())
                                    .with(user(details(seller))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("거래 상태 전이와 버전")
    class TradeStatusTransition {

        @Test
        @DisplayName("판매 중, 예약 중, 판매 완료 사이는 어느 방향이든 바꿀 수 있다")
        void everyTransitionIsAllowed() throws Exception {
            long version = 0;
            for (String next :
                    List.of("RESERVED", "SOLD", "ON_SALE", "SOLD", "RESERVED", "ON_SALE")) {
                JsonNode changed =
                        json(
                                tradeStatus(seller, publicId, next, version)
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.tradeStatus").value(next)));
                assertThat(changed.get("version").asLong()).isEqualTo(version + 1);
                version = changed.get("version").asLong();
                clear();
            }
        }

        @Test
        @DisplayName("같은 상태로 바꾸면 오류 없이 현재 상태를 돌려주고 버전이 오르지 않는다")
        void sameStatusKeepsVersion() throws Exception {
            tradeStatus(seller, publicId, "ON_SALE", 0)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tradeStatus").value("ON_SALE"))
                    .andExpect(jsonPath("$.version").value(0));
            clear();

            assertThat(versionOf()).isZero();
        }

        @Test
        @DisplayName("버전이 다르면 409이고 아무것도 바뀌지 않는다. 오류 본문에 연락처가 없다")
        void staleVersionConflicts() throws Exception {
            tradeStatus(seller, publicId, "RESERVED", 0).andExpect(status().isOk());
            clear();

            String conflict =
                    tradeStatus(seller, publicId, "SOLD", 0)
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.code").value("MARKET_006"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString(StandardCharsets.UTF_8);
            clear();
            String updateConflict =
                    update(seller, publicId, listingBody("늦은 수정", 1000L, 0L))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.code").value("MARKET_006"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString(StandardCharsets.UTF_8);
            clear();

            assertThat(conflict + updateConflict).doesNotContain("010-1234-5678");
            assertThat(tradeStatusOf()).isEqualTo("RESERVED");
            assertThat(title()).isEqualTo("데크 판매합니다");
        }

        @Test
        @DisplayName("모르는 거래 상태, 빈 값, 음수 버전은 400이다")
        void rejectsInvalidTradeStatusRequest() throws Exception {
            for (String body :
                    List.of(
                            "{\"tradeStatus\":\"DELETED\",\"version\":0}",
                            "{\"version\":0}",
                            "{\"tradeStatus\":\"SOLD\"}",
                            "{\"tradeStatus\":\"SOLD\",\"version\":-1}")) {
                mockMvc.perform(
                                patch(LISTINGS + "/{id}/trade-status", publicId)
                                        .with(csrf())
                                        .with(user(details(seller)))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isBadRequest());
            }

            assertThat(tradeStatusOf()).isEqualTo("ON_SALE");
        }
    }

    @Nested
    @DisplayName("관리자 운영 상태")
    class Moderation {

        @Test
        @DisplayName("운영 상태 변경은 비로그인 401, 일반 회원과 작성자 403이다")
        void onlyAdminCanModerate() throws Exception {
            mockMvc.perform(
                            patch("/api/v1/admin/market-listings/{id}/moderation-status", publicId)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"moderationStatus\":\"HIDDEN\"}"))
                    .andExpect(status().isUnauthorized());
            for (Member member : List.of(seller, other)) {
                moderate(member, "HIDDEN").andExpect(status().isForbidden());
            }

            assertThat(postStatus()).isEqualTo("NORMAL");
        }

        @Test
        @DisplayName("숨긴 판매글은 작성자를 포함한 일반 회원에게 404이고 목록, 공유 미리보기, 수정에서 빠진다")
        void hiddenListingIsInvisibleToMembers() throws Exception {
            moderate(admin, "HIDDEN").andExpect(status().isNoContent());
            clear();

            for (Member member : List.of(seller, other)) {
                mockMvc.perform(get(LISTINGS + "/{id}", publicId).with(user(details(member))))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("MARKET_001"));
            }
            mockMvc.perform(get(LISTINGS + "/{id}/share-preview", publicId))
                    .andExpect(status().isNotFound());
            update(seller, publicId, listingBody("숨김 중 수정", 1000L, 1L))
                    .andExpect(status().isNotFound());
            tradeStatus(seller, publicId, "SOLD", 1).andExpect(status().isNotFound());
            assertThat(listedIds(seller)).doesNotContain(publicId);
            // 관리자 세션은 숨긴 글을 볼 수 있다.
            mockMvc.perform(get(LISTINGS + "/{id}", publicId).with(user(details(admin))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HIDDEN"));

            moderate(admin, "NORMAL").andExpect(status().isNoContent());
            clear();
            mockMvc.perform(get(LISTINGS + "/{id}", publicId).with(user(details(other))))
                    .andExpect(status().isOk());
            assertThat(listedIds(other)).contains(publicId);
        }

        @Test
        @DisplayName("운영 상태로 삭제나 임시조치를 줄 수 없다(403). 삭제는 DELETE로만 한다")
        void moderationStatusIsLimited() throws Exception {
            for (String value : List.of("DELETED", "TEMPORARY_HOLD")) {
                moderate(admin, value).andExpect(status().isForbidden());
            }
            moderate(admin, "ARCHIVED").andExpect(status().isBadRequest());

            assertThat(postStatus()).isEqualTo("NORMAL");
        }

        @Test
        @DisplayName("임시조치 중인 판매글은 운영 상태 변경을 막아 복구 때 숨김이 풀리는 일이 없다")
        void temporaryHoldBlocksModeration() throws Exception {
            jdbc.update("UPDATE post SET status = 'TEMPORARY_HOLD' WHERE public_id = ?", publicId);
            clear();

            moderate(admin, "HIDDEN")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTENT_REPORT_004"));
            assertThat(postStatus()).isEqualTo("TEMPORARY_HOLD");
        }
    }

    @Nested
    @DisplayName("목록과 상세의 제한")
    class Limits {

        @Test
        @DisplayName("쪽은 1~100, 크기는 1~50만 받는다")
        void pageAndSizeBounds() throws Exception {
            for (String page : List.of("0", "-1", "101")) {
                mockMvc.perform(get(LISTINGS).param("page", page).with(user(details(other))))
                        .andExpect(status().isBadRequest());
            }
            for (String size : List.of("0", "-1", "51")) {
                mockMvc.perform(get(LISTINGS).param("size", size).with(user(details(other))))
                        .andExpect(status().isBadRequest());
            }
            mockMvc.perform(
                            get(LISTINGS)
                                    .param("page", "100")
                                    .param("size", "50")
                                    .with(user(details(other))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("검색어가 있으면 판매 완료 글도 찾고, 거래 상태를 주면 그 상태만 나온다")
        void keywordAndTradeStatusFilters() throws Exception {
            String token = "검색" + UUID.randomUUID().toString().substring(0, 8);
            String sold = createListing(seller, token + " 바인딩");
            String onSale = createListing(seller, token + " 부츠");
            tradeStatus(seller, sold, "SOLD", 0).andExpect(status().isOk());
            clear();

            assertThat(searchIds(token, null)).containsExactlyInAnyOrder(sold, onSale);
            assertThat(searchIds(token, "SOLD")).containsExactly(sold);
            assertThat(searchIds(token, "ON_SALE")).containsExactly(onSale);
            assertThat(searchIds(token, "RESERVED")).isEmpty();
        }

        @Test
        @DisplayName("목록에는 연락처가 없고, 상세는 캐시하지 않으며 작성자에게만 수정 권한을 알린다")
        void contactOnlyInDetail() throws Exception {
            String list =
                    mockMvc.perform(get(LISTINGS).param("size", "50").with(user(details(other))))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString(StandardCharsets.UTF_8);
            assertThat(list).contains(publicId).doesNotContain("010-1234-5678");

            mockMvc.perform(get(LISTINGS + "/{id}", publicId).with(user(details(other))))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(jsonPath("$.contact").value("010-1234-5678"))
                    .andExpect(jsonPath("$.canEdit").value(false))
                    .andExpect(jsonPath("$.canDelete").value(false));
            mockMvc.perform(get(LISTINGS + "/{id}", publicId).with(user(details(admin))))
                    .andExpect(jsonPath("$.canEdit").value(false))
                    .andExpect(jsonPath("$.canDelete").value(true));
        }

        @Test
        @DisplayName("가격과 무료 나눔, 연락처, 사진 주소가 규칙을 벗어난 등록은 400이다")
        void rejectsInvalidCreate() throws Exception {
            Map<String, Object> freeWithPrice = listingBody("나눔", 1000L, null);
            freeWithPrice.put("free", true);
            Map<String, Object> zeroPrice = listingBody("0원", 0L, null);
            Map<String, Object> overPrice = listingBody("비쌈", 20_000_001L, null);
            Map<String, Object> longContact = listingBody("연락처", 1000L, null);
            longContact.put("contact", "가".repeat(31));
            Map<String, Object> controlContact = listingBody("연락처", 1000L, null);
            controlContact.put("contact", "010\u0000-1234");
            Map<String, Object> externalImage = listingBody("사진", 1000L, null);
            externalImage.put("imageUrls", List.of("https://evil.example.com/tracker.png"));
            Map<String, Object> traversalImage = listingBody("사진", 1000L, null);
            traversalImage.put("imageUrls", List.of("public/posts/originals/../../secret.png"));
            Map<String, Object> unknownCategory = listingBody("분류", 1000L, null);
            unknownCategory.put("categoryCode", "NO_SUCH_CATEGORY");

            for (Map<String, Object> body :
                    List.of(
                            freeWithPrice,
                            zeroPrice,
                            overPrice,
                            longContact,
                            controlContact,
                            externalImage,
                            traversalImage)) {
                create(seller, body).andExpect(status().isBadRequest());
            }
            create(seller, unknownCategory)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MARKET_002"));
        }
    }

    private List<String> searchIds(String keyword, String tradeStatus) throws Exception {
        var request = get(LISTINGS).param("size", "50").param("keyword", keyword);
        if (tradeStatus != null) {
            request.param("tradeStatus", tradeStatus);
        }
        return ids(mockMvc.perform(request.with(user(details(other)))).andExpect(status().isOk()));
    }

    private List<String> listedIds(Member member) throws Exception {
        return ids(
                mockMvc.perform(
                                get(LISTINGS)
                                        .param("size", "50")
                                        .param("includeSold", "true")
                                        .with(user(details(member))))
                        .andExpect(status().isOk()));
    }

    private List<String> ids(ResultActions actions) throws Exception {
        List<String> ids = new java.util.ArrayList<>();
        json(actions).get("content").forEach(item -> ids.add(item.get("publicId").asText()));
        return ids;
    }

    private ResultActions create(Member member, Map<String, Object> body) throws Exception {
        return mockMvc.perform(
                post(LISTINGS)
                        .with(csrf())
                        .with(user(details(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(write(body)));
    }

    private String createListing(Member member, String title) throws Exception {
        return json(create(member, listingBody(title, 350_000L, null))
                        .andExpect(status().isCreated()))
                .get("publicId")
                .asText();
    }

    private ResultActions update(Member member, String id, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(
                put(LISTINGS + "/{id}", id)
                        .with(csrf())
                        .with(user(details(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(write(body)));
    }

    private ResultActions tradeStatus(Member member, String id, String status, long version)
            throws Exception {
        return mockMvc.perform(
                patch(LISTINGS + "/{id}/trade-status", id)
                        .with(csrf())
                        .with(user(details(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tradeStatus\":\""
                                        + status
                                        + "\",\"version\":"
                                        + version
                                        + "}"));
    }

    private ResultActions moderate(Member member, String status) throws Exception {
        return mockMvc.perform(
                patch("/api/v1/admin/market-listings/{id}/moderation-status", publicId)
                        .with(csrf())
                        .with(user(details(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moderationStatus\":\"" + status + "\"}"));
    }

    private static Map<String, Object> listingBody(String title, Long price, Long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryCode", "SNOWBOARD");
        body.put("title", title);
        body.put("content", "사용감이 조금 있습니다.");
        body.put("productCondition", "GOOD");
        body.put("transactionMethod", "BOTH");
        body.put("price", price);
        body.put("negotiable", false);
        body.put("free", false);
        body.put("contact", "010-1234-5678");
        body.put("imageUrls", List.of());
        if (version != null) {
            body.put("version", version);
        }
        return body;
    }

    private String title() {
        return jdbc.queryForObject(
                "SELECT title FROM post WHERE public_id = ?", String.class, publicId);
    }

    private String postStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM post WHERE public_id = ?", String.class, publicId);
    }

    private Long price() {
        return listingColumn("price", Long.class);
    }

    private String tradeStatusOf() {
        return listingColumn("trade_status", String.class);
    }

    private Long versionOf() {
        return listingColumn("version", Long.class);
    }

    private <T> T listingColumn(String column, Class<T> type) {
        return jdbc.queryForObject(
                "SELECT ml."
                        + column
                        + " FROM market_listing ml JOIN post p ON p.post_id = ml.post_id"
                        + " WHERE p.public_id = ?",
                type,
                publicId);
    }

    /** 요청마다 새 영속성 컨텍스트에서 읽게 한다. 실제 요청은 서로 다른 트랜잭션이다. */
    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    private CustomUserDetails details(Member member) {
        return new CustomUserDetails(member);
    }

    private String write(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Member saveMember(String unique, String suffix, Role role) {
        return memberRepository.save(
                Member.builder()
                        .publicId(UUID.randomUUID().toString())
                        .email(unique + "-" + suffix + "@example.com")
                        .nickname((suffix + unique).substring(0, 12))
                        .role(role)
                        .build());
    }
}
