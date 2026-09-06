package com.ikae.snowthing.domain.comment.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.post.entity.Post;

class CommentResponseTest {

    @Test
    @DisplayName("일반 회원 댓글은 클라이언트에 writerIp를 노출하지 않는다 (null)")
    void from_memberComment_doesNotExposeWriterIp() {
        Member member =
                Member.builder()
                        .email("user@example.com")
                        .password("encodedPassword")
                        .nickname("보더스노우")
                        .role(Role.ROLE_USER)
                        .build();
        ReflectionTestUtils.setField(member, "publicId", "mbr_public_123");

        Post post = Post.builder().title("게시글").content("내용").build();
        ReflectionTestUtils.setField(post, "id", 1L);

        Comment comment =
                Comment.create(post, member, null, "일반 회원 댓글", "192.168.0.15", false, null);
        ReflectionTestUtils.setField(comment, "id", 10L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());

        CommentResponse response = CommentResponse.from(comment);

        assertThat(response.isAnonymous()).isFalse();
        assertThat(response.writerIp()).isNull();
        assertThat(response.writer()).isNotNull();
        assertThat(response.writer().nickname()).isEqualTo("보더스노우");
        assertThat(response.writerName()).isEqualTo("보더스노우");
    }

    @Test
    @DisplayName("익명 댓글은 마스킹된 writerIp를 전달하고 writerName에 포함한다")
    void from_anonymousComment_exposesMaskedWriterIp() {
        Post post = Post.builder().title("게시글").content("내용").build();
        ReflectionTestUtils.setField(post, "id", 1L);

        Comment comment = Comment.create(post, null, null, "익명 댓글", "211.234.120.45", true, "1234");
        ReflectionTestUtils.setField(comment, "id", 20L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());

        CommentResponse response = CommentResponse.from(comment);

        assertThat(response.isAnonymous()).isTrue();
        assertThat(response.writerIp()).isEqualTo("211.234.***.***");
        assertThat(response.writer()).isNull();
        assertThat(response.writerName()).isEqualTo("익명 (211.234.***.***)");
    }

    @Test
    @DisplayName("익명 댓글에 IP가 없는 경우 writerName은 '익명'을 안전하게 반환한다")
    void writerName_anonymousWithoutIp_fallsBackGracefully() {
        CommentResponse response =
                new CommentResponse(
                        30L,
                        1L,
                        null,
                        null,
                        true,
                        null,
                        "IP 없는 익명 댓글",
                        false,
                        0,
                        java.util.List.of(),
                        false,
                        null,
                        false,
                        false,
                        LocalDateTime.now());

        assertThat(response.writerName()).isEqualTo("익명");
    }
}
