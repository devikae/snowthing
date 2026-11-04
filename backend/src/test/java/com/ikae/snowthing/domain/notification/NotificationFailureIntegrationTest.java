package com.ikae.snowthing.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.event.CommentCreatedEvent;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.notification.service.NotificationWriter;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 알림 저장이 실패해도 이미 커밋된 댓글은 남고 댓글 작성 요청도 성공하는지 본다. 알림 저장만 실패하도록 바꾼다. */
@SpringBootTest(
        properties = {
            // 클래스가 끝나면 컨텍스트를 닫는다. create-drop이면 닫힐 때 공유 테스트 스키마를 지워 다른 컨텍스트가 깨진다.
            "spring.jpa.hibernate.ddl-auto=update"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationFailureIntegrationTest {

    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private NotificationWriter notificationWriter;

    private final List<Long> memberIds = new ArrayList<>();
    private String postPublicId;

    @AfterEach
    void tearDown() {
        if (postPublicId != null) {
            Long postId =
                    jdbc.queryForObject(
                            "SELECT post_id FROM post WHERE public_id = ?",
                            Long.class,
                            postPublicId);
            jdbc.update("DELETE FROM comment WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post_content_image_reference WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("알림 저장이 실패해도 댓글은 저장되고 작성 요청은 성공한다")
    void commentSurvivesNotificationFailure() {
        given(notificationWriter.createForComment(any()))
                .willThrow(new DataIntegrityViolationException("알림 저장 실패"));
        categoryRepository
                .findByCode("FREE")
                .orElseGet(() -> categoryRepository.save(new PostCategory("자유게시판", "FREE")));
        CustomUserDetails author = new CustomUserDetails(saveMember("실패글쓴이"));
        CustomUserDetails commenter = new CustomUserDetails(saveMember("실패댓글러"));
        postPublicId =
                postService
                        .createPost(
                                new PostCreateRequest(
                                        "FREE", "실패 테스트", "본문", false, null, List.of()),
                                author,
                                "127.0.0.1")
                        .publicId();

        CommentResponse response =
                commentService.createComment(
                        postPublicId,
                        new CommentCreateRequest(null, "남아야 하는 댓글", false, null),
                        commenter,
                        "127.0.0.1");

        verify(notificationWriter).createForComment(any(CommentCreatedEvent.class));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM comment WHERE comment_id = ?",
                                Long.class,
                                response.commentId()))
                .isEqualTo(1L);
    }

    private Member saveMember(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email("noti-fail-" + suffix + "@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname(label + "-" + suffix)
                                .role(Role.ROLE_USER)
                                .status(MemberStatus.ACTIVE)
                                .build());
        memberIds.add(member.getId());
        return member;
    }
}
