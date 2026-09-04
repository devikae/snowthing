package com.ikae.snowthing.domain.comment.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommentResponseTest {

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
                LocalDateTime.now());
    }
}
