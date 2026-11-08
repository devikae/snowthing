package com.ikae.snowthing.domain.post.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PostContentVersionTest {

    private final PostCategory free = category(1L, "FREE");
    private final PostCategory qna = category(2L, "QNA");

    @Test
    @DisplayName("새 게시글의 내용 버전은 0이다")
    void startsAtZero() {
        assertThat(post().getContentVersion()).isZero();
    }

    @Test
    @DisplayName("제목·본문·카테고리 중 하나라도 바뀌면 내용 버전이 1 오른다")
    void updateIncrementsWhenChanged() {
        Post post = post();

        post.update("새 제목", "본문", free);
        assertThat(post.getContentVersion()).isEqualTo(1);

        post.update("새 제목", "새 본문", free);
        assertThat(post.getContentVersion()).isEqualTo(2);

        post.update("새 제목", "새 본문", qna);
        assertThat(post.getContentVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 값으로 수정하면 내용 버전이 오르지 않는다. 같은 ID의 다른 카테고리 인스턴스도 같은 카테고리로 본다")
    void updateWithSameValuesKeepsVersion() {
        Post post = post();

        post.update("제목", "본문", category(1L, "FREE"));

        assertThat(post.getContentVersion()).isZero();
    }

    @Test
    @DisplayName("상태가 바뀔 때만 내용 버전이 오른다")
    void statusChangeIncrements() {
        Post post = post();

        post.changeStatus(PostStatus.NORMAL);
        assertThat(post.getContentVersion()).isZero();

        post.changeStatus(PostStatus.HIDDEN);
        assertThat(post.getContentVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제하면 내용 버전이 1 오르고, 이미 삭제된 글을 다시 삭제해도 더 오르지 않는다")
    void softDeleteIncrementsOnce() {
        Post post = post();

        post.softDelete();
        post.softDelete();

        assertThat(post.getContentVersion()).isEqualTo(1);
        assertThat(post.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("조회수·반응 수·이미지 변경은 내용 버전과 무관하다")
    void countersDoNotIncrement() {
        Post post = post();

        post.increaseViewCount();
        post.increaseLikeCount();
        post.increaseCommentCount();
        post.updateImageStatus(true);

        assertThat(post.getContentVersion()).isZero();
    }

    private Post post() {
        return Post.builder()
                .category(free)
                .title("제목")
                .content("본문")
                .writerIp("127.0.0.1")
                .isAnonymous(false)
                .build();
    }

    private static PostCategory category(Long id, String code) {
        PostCategory category = PostCategory.builder().code(code).name(code).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
