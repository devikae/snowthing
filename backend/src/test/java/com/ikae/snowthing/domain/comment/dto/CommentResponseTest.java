package com.ikae.snowthing.domain.comment.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

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
    @DisplayName("익명 댓글은 마스킹된 writerIp를 전달하고 writerName에 축약 IP를 포함한다")
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
        assertThat(response.writerName()).isEqualTo("ㅇㅇ(211.234)");
    }

    @Test
    @DisplayName("[익명 댓글 IP 마스킹] 익명 댓글이고 IP가 주어지면 앞 두 자리만 포함하여 'ㅇㅇ(xxx.xxx)' 형태로 반환해야 한다")
    void writerName_AnonymousWithIp_ReturnsShortIp() {
        CommentResponse response = createResponse(true, "127.0.***.***", null);

        assertThat(response.writerName()).isEqualTo("ㅇㅇ(127.0)");
    }

    @Test
    @DisplayName("[익명 댓글 일반 IPv4] 익명 댓글이고 마스킹 전 4옥텟 IP라도 앞 두 자리만 반환해야 한다")
    void writerName_AnonymousWithRawIp_ReturnsShortIp() {
        CommentResponse response = createResponse(true, "211.234.12.34", null);

        assertThat(response.writerName()).isEqualTo("ㅇㅇ(211.234)");
    }

    @Test
    @DisplayName("[익명 댓글 IP 누락] 익명 댓글인데 IP가 없거나 빈 값이면 'ㅇㅇ'만 반환해야 한다")
    void writerName_AnonymousWithoutIp_ReturnsOnlyAnonymousName() {
        CommentResponse responseNullIp = createResponse(true, null, null);
        CommentResponse responseBlankIp = createResponse(true, "   ", null);

        assertThat(responseNullIp.writerName()).isEqualTo("ㅇㅇ");
        assertThat(responseBlankIp.writerName()).isEqualTo("ㅇㅇ");
    }

    @Test
    @DisplayName("[회원 댓글] 비익명 회원이면 회원의 닉네임을 반환해야 한다")
    void writerName_Member_ReturnsNickname() {
        CommentResponse.WriterResponse writer =
                new CommentResponse.WriterResponse("user-uuid", "스노우보더", "profile.jpg");
        CommentResponse response = createResponse(false, "127.0.0.1", writer);

        assertThat(response.writerName()).isEqualTo("스노우보더");
    }

    @Test
    @DisplayName("[회원 댓글 작성자 누락] 비익명인데 회원 정보가 null이면 'ㅇㅇ'를 기본값으로 반환해야 한다")
    void writerName_MemberNull_ReturnsAnonymousName() {
        CommentResponse response = createResponse(false, "127.0.0.1", null);

        assertThat(response.writerName()).isEqualTo("ㅇㅇ");
    }

    private CommentResponse createResponse(
            boolean isAnonymous, String writerIp, CommentResponse.WriterResponse writer) {
        return new CommentResponse(
                1L,
                10L,
                null,
                writer,
                isAnonymous,
                writerIp,
                "댓글 내용입니다.",
                false,
                0L,
                List.of(),
                false,
                null,
                false,
                false,
                false,
                false,
                LocalDateTime.now());
    }
}
