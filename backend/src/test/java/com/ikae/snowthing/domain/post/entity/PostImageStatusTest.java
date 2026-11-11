package com.ikae.snowthing.domain.post.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostImageStatusTest {

    @Test
    @DisplayName("본문에 넣은 사진만 있어도 목록 사진 표시가 켜진다")
    void inlineImageTurnsOnHasImage() {
        Post post = post();

        post.replaceImages(List.of());
        post.replaceContentImageKeys(Set.of("posts/2026/11/a.webp"));

        assertThat(post.isHasImage()).isTrue();
    }

    @Test
    @DisplayName("첨부 사진만 있어도 켜지고, 첨부와 본문 사진이 모두 없으면 꺼진다")
    void galleryImageAndNoImage() {
        Post post = post();

        post.replaceContentImageKeys(Set.of());
        post.replaceImages(
                List.of(PostImage.builder().imageUrl("posts/2026/11/b.webp").sortOrder(1).build()));
        assertThat(post.isHasImage()).isTrue();

        post.replaceImages(List.of());
        assertThat(post.isHasImage()).isFalse();
    }

    @Test
    @DisplayName("첨부 사진을 지워도 본문 사진이 남아 있으면 켜진 채로 둔다")
    void keepsOnWhileInlineImageRemains() {
        Post post = post();
        post.replaceContentImageKeys(Set.of("posts/2026/11/a.webp"));

        post.replaceImages(List.of());

        assertThat(post.isHasImage()).isTrue();
    }

    private Post post() {
        return Post.builder()
                .category(PostCategory.builder().code("FREE").name("자유게시판").build())
                .title("제목")
                .content("본문")
                .writerIp("127.0.0.1")
                .isAnonymous(false)
                .build();
    }
}
