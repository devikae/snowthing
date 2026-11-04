package com.ikae.snowthing.domain.aimember;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ikae.snowthing.domain.aimember.AiMemberRoster.AiMember;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.PostView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.Reaction;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.ComposeResponse;
import com.ikae.snowthing.domain.aimember.persona.Persona;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.global.security.SharedRequestLimiter;
import com.ikae.snowthing.global.security.SharedRequestLimiter.Decision;

/** 실제 PostService·CommentService와 MySQL을 거쳐 AI 멤버 글·댓글이 저장되는지 본다. 봇 서버만 대체한다. */
@SpringBootTest(
        properties = {
            "snowthing.ai-members.enabled=true",
            "snowthing.ai-members.persona-file=",
            "spring.datasource.hikari.maximum-pool-size=2",
            // 클래스가 끝나면 컨텍스트를 닫는다. create-drop이면 닫힐 때 공유 테스트 스키마를 지워 다른 컨텍스트가 깨진다.
            "spring.jpa.hibernate.ddl-auto=update"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiMemberIntegrationTest {

    private static final String WRITER_IP = "211.36.10.20";

    @Autowired private AiMemberActionService actionService;
    @Autowired private AiMemberQueryRepository queryRepository;
    @Autowired private AiMemberRoster roster;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private AiComposeGateway gateway;
    @MockitoBean private SharedRequestLimiter sharedLimiter;

    private AiMember aiMember;
    private Long userId;

    @BeforeEach
    void setUp() {
        for (String code : AiMemberQueryRepository.BOARDS) {
            categories
                    .findByCode(code)
                    .orElseGet(
                            () ->
                                    categories.save(
                                            PostCategory.builder().code(code).name(code).build()));
        }
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member ai =
                memberRepository.save(
                        Member.builder()
                                .email("ai-" + unique + "@snowthing.invalid")
                                .nickname("ai" + unique)
                                .role(Role.ROLE_USER)
                                .build());
        userId =
                memberRepository
                        .save(
                                Member.builder()
                                        .email("user-" + unique + "@example.com")
                                        .nickname("user" + unique)
                                        .role(Role.ROLE_USER)
                                        .build())
                        .getId();
        aiMember =
                new AiMember(
                        new Persona("a1", ai.getEmail(), ai.getNickname(), WRITER_IP, "카빙러"),
                        ai.getId(),
                        ai.getPublicId());
        roster.replace(List.of(aiMember));
        given(sharedLimiter.acquire(anyList())).willReturn(new Decision(true, 0, null));
    }

    @AfterEach
    void tearDown() {
        for (Long memberId : List.of(aiMember.memberId(), userId)) {
            jdbc.update("DELETE FROM comment WHERE member_id = ?", memberId);
            jdbc.update(
                    "DELETE FROM comment WHERE post_id IN (SELECT post_id FROM post WHERE member_id = ?)",
                    memberId);
            jdbc.update("DELETE FROM post WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
        roster.replace(List.of());
    }

    @Test
    @DisplayName("AI 글은 PostService로 저장되고 캐릭터 가상 IP가 남는다")
    void writesPostThroughPostService() {
        LocalDateTime now = LocalDateTime.now();
        given(gateway.compose(any()))
                .willReturn(new ComposeResponse("r", "오늘 곤지암 어땠음", "사람 많더라", null));

        actionService.writePost(aiMember, now);

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT title, writer_ip FROM post WHERE member_id = ?",
                        aiMember.memberId());
        assertThat(row.get("title")).isEqualTo("오늘 곤지암 어땠음");
        assertThat(row.get("writer_ip")).isEqualTo(WRITER_IP);
        assertThat(
                        queryRepository
                                .countPostsSince(List.of(aiMember.memberId()), now.minusMinutes(1))
                                .get(aiMember.memberId()))
                .isEqualTo(1);
        assertThat(queryRepository.findTitlesSince(List.of(aiMember.memberId()), now.minusDays(7)))
                .containsExactly("오늘 곤지암 어땠음");
    }

    @Test
    @DisplayName("사람 글에 단 AI 댓글은 CommentService로 저장되고 익명게시판이면 익명이다")
    void commentsOnUserPostAnonymously() {
        LocalDateTime now = LocalDateTime.now();
        Long categoryId = categories.findByCode("ANONYMOUS").map(PostCategory::getId).orElseThrow();
        String publicId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO post (public_id, member_id, category_id, title, content, writer_ip,"
                        + " is_anonymous, view_count, comment_count, like_count, dislike_count,"
                        + " has_image, status, is_deleted, content_version, created_at, updated_at)"
                        + " VALUES (?, ?, ?, '익명 질문', '본문', '58.120.1.1', true, 0, 0, 0, 0,"
                        + " false, 'NORMAL', false, 0, ?, ?)",
                publicId,
                userId,
                categoryId,
                now.minusHours(1),
                now.minusHours(1));
        PostView post =
                queryRepository.findRecentPosts(now.minusHours(2)).stream()
                        .filter(view -> view.publicId().equals(publicId))
                        .findFirst()
                        .orElseThrow();
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", null, "ㅇㅈ합니다", null));

        boolean written =
                actionService.writeComment(
                        new Reaction(aiMember.memberId(), post, null),
                        queryRepository.findComments(List.of(post.postId())),
                        now);

        assertThat(written).isTrue();
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT content, writer_ip, is_anonymous FROM comment WHERE member_id = ?",
                        aiMember.memberId());
        assertThat(row.get("content")).isEqualTo("ㅇㅈ합니다");
        assertThat(row.get("writer_ip")).isEqualTo(WRITER_IP);
        assertThat(row.get("is_anonymous")).isIn(true, 1, 1L);
        assertThat(queryRepository.findComments(List.of(post.postId()))).hasSize(1);
        assertThat(queryRepository.ipPrefixUsedByOthers(WRITER_IP, aiMember.memberId())).isFalse();
        assertThat(queryRepository.ipPrefixUsedByOthers("58.120.9.9", aiMember.memberId()))
                .isTrue();
    }
}
