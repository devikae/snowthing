package com.ikae.snowthing.domain.admin.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostService;

@SpringBootTest
class AdminPostVisibilityIntegrationTest {
    @Autowired AdminPostService admin;
    @Autowired PostService publicPosts;
    @Autowired PostRepository posts;
    @Autowired PostCategoryRepository categories;

    @Test
    void blindedPostIsHiddenFromNormalQueriesButRemainsAuditableAndCanBeUnblinded() {
        var category =
                categories
                        .findByCode("FREE")
                        .orElseGet(
                                () ->
                                        categories.save(
                                                PostCategory.builder()
                                                        .code("FREE")
                                                        .name("Free")
                                                        .build()));
        var post =
                posts.save(
                        Post.builder()
                                .category(category)
                                .title("visibility regression")
                                .content("body")
                                .writerIp("192.0.2.1")
                                .isAnonymous(true)
                                .build());
        var id = post.getPublicId();
        admin.toggleBlindPost(id, true);
        assertThat(posts.findByPublicId(id)).isEmpty();
        assertThatThrownBy(() -> publicPosts.getPostDetail(id, null))
                .isInstanceOf(com.ikae.snowthing.global.exception.CustomAuthException.class);
        assertThat(admin.getPosts(false, true, PageRequest.of(0, 100)).getContent())
                .extracting("publicId")
                .contains(id);
        assertThat(admin.getPosts(true, true, PageRequest.of(0, 100)).getContent())
                .extracting("publicId")
                .doesNotContain(id);
        admin.toggleBlindPost(id, false);
        assertThat(posts.findByPublicId(id)).isPresent();
        assertThat(admin.getPosts(false, true, PageRequest.of(0, 100)).getContent())
                .extracting("publicId")
                .doesNotContain(id);
    }
}
