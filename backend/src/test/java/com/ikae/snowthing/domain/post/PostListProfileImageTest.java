package com.ikae.snowthing.domain.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.dto.PostListResponse;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;

class PostListProfileImageTest {
    @Test
    void onlyNonAnonymousAuthorsExposeCurrentProfileImage() {
        Member member =
                Member.builder()
                        .nickname("writer")
                        .profileImageUrl("https://images.example/first.jpg")
                        .build();
        PostCategory category = PostCategory.builder().code("FREE").name("Free").build();
        Post named = Post.builder().member(member).category(category).writerIp("127.0.0.1").build();
        Post anonymous =
                Post.builder()
                        .member(member)
                        .category(category)
                        .writerIp("127.0.0.1")
                        .isAnonymous(true)
                        .build();

        assertThat(PostListResponse.from(named).writerProfileImageUrl())
                .isEqualTo("https://images.example/first.jpg");
        assertThat(PostListResponse.from(anonymous).writerProfileImageUrl()).isNull();
        assertThat(PostListResponse.from(anonymous).writerPublicId()).isNull();
        member.updateProfile(member.getNickname(), null, null, "https://images.example/second.jpg");
        assertThat(
                        PostListResponse.from(named)
                                .withThumbnailImageUrl("thumbnail")
                                .writerProfileImageUrl())
                .isEqualTo("https://images.example/second.jpg");
    }
}
