package com.ikae.snowthing.domain.comment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommentTest {

    @Test
    @DisplayName("루트 댓글(parent == null)에서 rootParent를 호출하면 자기 자신을 반환한다")
    void rootComment_rootParent_returnsSelf() {
        Comment root =
                Comment.builder().content("루트 댓글").writerIp("127.0.0.1").isAnonymous(false).build();

        assertThat(root.rootParent()).isSameAs(root);
    }

    @Test
    @DisplayName("2-depth 대댓글에서 rootParent를 호출하면 부모(루트 댓글)를 반환한다")
    void childComment_rootParent_returnsParent() {
        Comment root =
                Comment.builder().content("루트 댓글").writerIp("127.0.0.1").isAnonymous(false).build();

        Comment child =
                Comment.builder()
                        .parent(root)
                        .content("대댓글")
                        .writerIp("127.0.0.1")
                        .isAnonymous(false)
                        .build();

        assertThat(child.rootParent()).isSameAs(root);
    }

    @Test
    @DisplayName("3-depth 이상의 깊은 계층 구조가 생기더라도 rootParent는 최상위 루트 댓글을 끝까지 탐색해 반환한다")
    void deepChildComment_rootParent_traversesToRoot() {
        Comment grandfather =
                Comment.builder()
                        .content("할아버지(루트) 댓글")
                        .writerIp("127.0.0.1")
                        .isAnonymous(false)
                        .build();

        Comment father =
                Comment.builder()
                        .parent(grandfather)
                        .content("아버지 대댓글")
                        .writerIp("127.0.0.1")
                        .isAnonymous(false)
                        .build();

        Comment grandson =
                Comment.builder()
                        .parent(father)
                        .content("손자 대대댓글 (3-depth 이상)")
                        .writerIp("127.0.0.1")
                        .isAnonymous(false)
                        .build();

        assertThat(grandson.rootParent()).isSameAs(grandfather);
    }
}
