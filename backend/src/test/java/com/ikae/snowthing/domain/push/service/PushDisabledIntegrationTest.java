package com.ikae.snowthing.domain.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 테스트 설정에는 VAPID 키가 없다. 이때 발송은 건너뛰고 오류가 없으며, 구독 등록만 비활성으로 답한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PushDisabledIntegrationTest {

    private static final String IP = "127.0.0.1";

    @MockitoBean private PushHttpClient pushHttpClient;

    @Autowired private MockMvc mockMvc;
    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private Member author;
    private Member commenter;
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
        for (Member member : Arrays.asList(author, commenter)) {
            if (member != null) {
                jdbc.update("DELETE FROM notification WHERE receiver_id = ?", member.getId());
                jdbc.update("DELETE FROM member WHERE member_id = ?", member.getId());
            }
        }
    }

    @Test
    @DisplayName("키가 없으면 설정은 꺼짐, 등록은 503, 해지는 204다")
    void apisReportDisabled() throws Exception {
        author = saveMember("꺼짐");
        CustomUserDetails me = new CustomUserDetails(author);

        mockMvc.perform(get("/api/v1/push/config").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.publicKey").doesNotExist());
        mockMvc.perform(
                        put("/api/v1/push/subscriptions")
                                .with(csrf())
                                .with(user(me))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\","
                                                + "\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PUSH_001"));
        mockMvc.perform(
                        delete("/api/v1/push/subscriptions")
                                .with(csrf())
                                .with(user(me))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("키가 없어도 댓글 알림은 저장되고 푸시 서비스는 부르지 않는다")
    void notificationSavedWithoutPush() throws Exception {
        author = saveMember("글쓴이");
        commenter = saveMember("댓글러");
        categoryRepository
                .findByCode("FREE")
                .orElseGet(() -> categoryRepository.save(new PostCategory("자유게시판", "FREE")));
        postPublicId =
                postService
                        .createPost(
                                new PostCreateRequest("FREE", "제목", "본문", false, null, List.of()),
                                new CustomUserDetails(author),
                                IP)
                        .publicId();

        commentService.createComment(
                postPublicId,
                new CommentCreateRequest(null, "댓글", false, null),
                new CustomUserDetails(commenter),
                IP);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM notification WHERE receiver_id = ?",
                                Long.class,
                                author.getId()))
                .isEqualTo(1L);
        Thread.sleep(300);
        verifyNoInteractions(pushHttpClient);
    }

    private Member saveMember(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(
                Member.builder()
                        .email("push-off-" + suffix + "@example.com")
                        .password(passwordEncoder.encode("Password123!"))
                        .nickname(label + "-" + suffix)
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build());
    }
}
