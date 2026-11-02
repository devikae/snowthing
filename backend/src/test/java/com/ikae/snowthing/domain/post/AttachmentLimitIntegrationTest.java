package com.ikae.snowthing.domain.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.market.entity.MarketCategory;
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
class AttachmentLimitIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MemberRepository members;
    @Autowired private PostCategoryRepository postCategories;
    @Autowired private MarketCategoryRepository marketCategories;
    private CustomUserDetails writer;

    @BeforeEach
    void setup() {
        writer =
                new CustomUserDetails(
                        members.save(
                                Member.builder()
                                        .email("attachment-limit@test.invalid")
                                        .nickname("첨부검증")
                                        .role(Role.ROLE_USER)
                                        .build()));
        for (String code : List.of("FREE", "MARKET"))
            postCategories
                    .findByCode(code)
                    .orElseGet(
                            () ->
                                    postCategories.save(
                                            PostCategory.builder().code(code).name(code).build()));
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
    }

    private List<String> images(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "public/posts/originals/test-" + i + ".jpg")
                .toList();
    }

    @Test
    void generalPostAcceptsTenAndRejectsElevenOnCreateAndUpdate() throws Exception {
        Map<String, Object> body =
                new HashMap<>(
                        Map.of(
                                "categoryCode",
                                "FREE",
                                "title",
                                "Attachment test",
                                "content",
                                "text",
                                "isAnonymous",
                                false,
                                "imageUrls",
                                images(10)));
        String response =
                mockMvc.perform(
                                post("/api/v1/posts")
                                        .with(csrf())
                                        .with(user(writer))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(mapper.writeValueAsString(body)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = mapper.readTree(response).get("publicId").asText();
        mockMvc.perform(get("/api/v1/posts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(10));
        body.remove("imageUrls");
        mockMvc.perform(
                        put("/api/v1/posts/{id}", id)
                                .with(csrf())
                                .with(user(writer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/posts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(10));
        body.put("imageUrls", List.of());
        mockMvc.perform(
                        put("/api/v1/posts/{id}", id)
                                .with(csrf())
                                .with(user(writer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/posts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(0));
        body.put("imageUrls", images(11));
        mockMvc.perform(
                        post("/api/v1/posts")
                                .with(csrf())
                                .with(user(writer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        put("/api/v1/posts/{id}", id)
                                .with(csrf())
                                .with(user(writer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        body.put("imageUrls", images(10));
        mockMvc.perform(
                        put("/api/v1/posts/{id}", id)
                                .with(csrf())
                                .with(user(writer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/posts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(10));
    }

    @Test
    void inlineImagesInContentDoNotConsumeAttachmentLimit() throws Exception {
        String inline =
                IntStream.range(0, 11)
                        .mapToObj(i -> "![inline](data:image/png;base64,AA==)")
                        .collect(java.util.stream.Collectors.joining("\n"));
        Map<String, Object> body =
                Map.of(
                        "categoryCode",
                        "FREE",
                        "title",
                        "Inline images",
                        "content",
                        inline,
                        "isAnonymous",
                        false,
                        "imageUrls",
                        images(10));
        mockMvc.perform(
                        post("/api/v1/posts")
                                .with(csrf())
                                .with(user(writer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void marketAcceptsTenAndRejectsElevenOnCreateAndUpdate() throws Exception {
        Map<String, Object> body =
                new HashMap<>(
                        Map.of(
                                "categoryCode",
                                "SNOWBOARD",
                                "title",
                                "Market attachment",
                                "content",
                                "text",
                                "productCondition",
                                "GOOD",
                                "transactionMethod",
                                "DIRECT",
                                "price",
                                10000,
                                "negotiable",
                                false,
                                "free",
                                false,
                                "contact",
                                "test-contact",
                                "imageUrls",
                                images(10)));
        String response =
                mockMvc.perform(
                                post("/api/v1/market-listings")
                                        .with(csrf())
                                        .with(user(writer))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(mapper.writeValueAsString(body)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = mapper.readTree(response).get("publicId").asText();
        mockMvc.perform(get("/api/v1/market-listings/{id}", id).with(user(writer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(10));
        body.put("imageUrls", images(11));
        mockMvc.perform(
                        post("/api/v1/market-listings")
                                .with(csrf())
                                .with(user(writer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        body.put("version", 0);
        mockMvc.perform(
                        put("/api/v1/market-listings/{id}", id)
                                .with(csrf())
                                .with(user(writer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        body.put("imageUrls", images(10));
        mockMvc.perform(
                        put("/api/v1/market-listings/{id}", id)
                                .with(csrf())
                                .with(user(writer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/market-listings/{id}", id).with(user(writer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(10));
    }
}
