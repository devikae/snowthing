package com.ikae.snowthing.domain.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.market.entity.MarketCategory;
import com.ikae.snowthing.domain.market.entity.MarketListing;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.market.repository.MarketListingRepository;
import com.ikae.snowthing.domain.market.service.MarketListingService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
class MarketListingDeleteIntegrationTest {
    @Autowired private TransactionTemplate transactions;
    @Autowired private MarketListingService listings;
    @Autowired private MarketListingRepository listingRepository;
    @Autowired private MarketCategoryRepository categories;
    @Autowired private PostCategoryRepository postCategories;
    @Autowired private PostRepository posts;
    @Autowired private MemberRepository members;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void deleteCommitsAcrossSeparateTransactions() {
        Seed seed =
                transactions.execute(
                        status -> {
                            String unique = UUID.randomUUID().toString();
                            Member member =
                                    members.save(
                                            Member.builder()
                                                    .email(unique + "@test.invalid")
                                                    .nickname(unique)
                                                    .role(Role.ROLE_USER)
                                                    .build());
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
                                    categories
                                            .findByCode("SNOWBOARD")
                                            .orElseGet(
                                                    () ->
                                                            categories.save(
                                                                    MarketCategory.builder()
                                                                            .code("SNOWBOARD")
                                                                            .name("Snowboard")
                                                                            .active(true)
                                                                            .build()));
                            Post post =
                                    posts.save(
                                            Post.builder()
                                                    .member(member)
                                                    .category(postCategory)
                                                    .title("Committed delete regression")
                                                    .content("Temporary test content")
                                                    .writerIp("192.0.2.0")
                                                    .isAnonymous(false)
                                                    .build());
                            listingRepository.save(
                                    MarketListing.builder()
                                            .post(post)
                                            .category(category)
                                            .productCondition(ProductCondition.GOOD)
                                            .transactionMethod(TransactionMethod.DIRECT)
                                            .price(10000L)
                                            .contact("test-contact")
                                            .build());
                            return new Seed(member, post.getId(), post.getPublicId());
                        });
        try {
            listings.delete(seed.publicId(), new CustomUserDetails(seed.member()));
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT is_deleted FROM post WHERE post_id = ?",
                                    Boolean.class,
                                    seed.postId()))
                    .isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT status FROM post WHERE post_id = ?",
                                    String.class,
                                    seed.postId()))
                    .isEqualTo("DELETED");
            assertThat(posts.findByPublicId(seed.publicId())).isEmpty();
        } finally {
            transactions.executeWithoutResult(
                    status -> {
                        jdbc.update("DELETE FROM market_listing WHERE post_id = ?", seed.postId());
                        jdbc.update("DELETE FROM post WHERE post_id = ?", seed.postId());
                        jdbc.update(
                                "DELETE FROM member WHERE member_id = ?", seed.member().getId());
                    });
        }
    }

    private record Seed(Member member, Long postId, String publicId) {}
}
