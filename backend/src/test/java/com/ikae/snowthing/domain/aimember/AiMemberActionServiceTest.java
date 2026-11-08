package com.ikae.snowthing.domain.aimember;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.ikae.snowthing.domain.aimember.AiMemberRoster.AiMember;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.PostView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.Reaction;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.ComposeRequest;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.ComposeResponse;
import com.ikae.snowthing.domain.aimember.persona.Persona;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.config.AiMemberProperties;
import com.ikae.snowthing.global.security.SharedRequestLimiter;
import com.ikae.snowthing.global.security.SharedRequestLimiter.Decision;

@ExtendWith(MockitoExtension.class)
class AiMemberActionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 19, 0);
    private static final AiMember MEMBER =
            new AiMember(
                    new Persona("a1", "a1@snowthing.invalid", "닉", "211.36.10.20", "설명"),
                    1L,
                    "public-1");

    @Mock private AiComposeGateway gateway;
    @Mock private AiMemberQueryRepository queryRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PostService postService;
    @Mock private CommentService commentService;
    @Mock private ChatService chatService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private SharedRequestLimiter sharedLimiter;
    @Mock private ScheduledExecutorService executor;

    private AiMemberActionService service;

    @BeforeEach
    void setUp() {
        AiMemberRoster roster = new AiMemberRoster();
        roster.replace(List.of(MEMBER));
        service =
                new AiMemberActionService(
                        new AiMemberProperties(true, "personas.yml", 100, List.of()),
                        gateway,
                        queryRepository,
                        roster,
                        memberRepository,
                        postService,
                        commentService,
                        chatService,
                        messagingTemplate,
                        sharedLimiter,
                        executor);
        lenient()
                .when(memberRepository.findStatusById(1L))
                .thenReturn(Optional.of(MemberStatus.ACTIVE));
    }

    @Test
    @DisplayName("정지된 AI 멤버는 봇 서버를 부르지 않고 글, 댓글, 채팅을 모두 건너뛴다")
    void suspendedMemberDoesNothing() {
        given(memberRepository.findStatusById(1L)).willReturn(Optional.of(MemberStatus.SUSPENDED));
        PostView post = new PostView(10L, "post-10", 100L, "FREE", "제목", "본문", NOW);

        service.writePost(MEMBER, NOW);
        boolean commented = service.writeComment(new Reaction(1L, post, null), List.of(), NOW);
        service.chat(List.of(MEMBER), 2, NOW);

        assertThat(commented).isFalse();
        verify(sharedLimiter, never()).acquire(anyList());
        verify(gateway, never()).compose(any());
        verify(postService, never()).createPost(any(), any(), any());
        verify(commentService, never()).createComment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("하루 호출 상한에 닿으면 봇 서버를 부르지 않는다")
    void skipsWhenBudgetExhausted() {
        given(sharedLimiter.acquire(anyList())).willReturn(new Decision(true, 3600, "key"));

        service.writePost(MEMBER, NOW);

        verify(gateway, never()).compose(any());
        verify(postService, never()).createPost(any(), any(), any());
    }

    @Test
    @DisplayName("생성 글에 연락처가 있으면 저장하지 않는다")
    void dropsPostWithContact() {
        allowBudget();
        given(gateway.compose(any()))
                .willReturn(new ComposeResponse("r", "제목", "카톡 010-1234-5678 주세요", null));

        service.writePost(MEMBER, NOW);

        verify(postService, never()).createPost(any(), any(), any());
    }

    @Test
    @DisplayName("최근 제목과 같은 글은 저장하지 않는다")
    void dropsDuplicateTitle() {
        allowBudget();
        given(queryRepository.findTitlesSince(any(), any())).willReturn(List.of("시즌권 고민"));
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", "시즌권 고민", "본문", null));

        service.writePost(MEMBER, NOW);

        verify(postService, never()).createPost(any(), any(), any());
    }

    @Test
    @DisplayName("글은 기존 PostService로 캐릭터의 가상 IP를 넘겨 저장한다")
    void writesPostWithPersonaIp() {
        allowBudget();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member()));
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", "제목", "본문", null));

        service.writePost(MEMBER, NOW);

        ArgumentCaptor<PostCreateRequest> request =
                ArgumentCaptor.forClass(PostCreateRequest.class);
        verify(postService).createPost(request.capture(), any(), eq("211.36.10.20"));
        assertThat(AiMemberQueryRepository.BOARDS).contains(request.getValue().categoryCode());
        ArgumentCaptor<ComposeRequest> composed = ArgumentCaptor.forClass(ComposeRequest.class);
        verify(gateway).compose(composed.capture());
        assertThat(composed.getValue().personas()).hasSize(1);
        assertThat(composed.getValue().personas().get(0).key()).isEqualTo("a1");
    }

    @Test
    @DisplayName("익명게시판 댓글은 익명으로 단다")
    void commentsAnonymouslyOnAnonymousBoard() {
        allowBudget();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member()));
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", null, "ㅇㅈ", null));
        PostView post = new PostView(10L, "post-10", 100L, "ANONYMOUS", "제목", "본문", NOW);

        boolean written = service.writeComment(new Reaction(1L, post, null), List.of(), NOW);

        assertThat(written).isTrue();
        ArgumentCaptor<CommentCreateRequest> request =
                ArgumentCaptor.forClass(CommentCreateRequest.class);
        verify(commentService)
                .createComment(eq("post-10"), request.capture(), any(), eq("211.36.10.20"));
        assertThat(request.getValue().isAnonymous()).isTrue();
        assertThat(request.getValue().anonymousPassword()).isNull();
    }

    @Test
    @DisplayName("봇 서버가 실패하면 댓글을 저장하지 않고 false를 돌려준다")
    void reportsFailedComment() {
        allowBudget();
        given(gateway.compose(any())).willThrow(new IllegalStateException("down"));
        PostView post = new PostView(10L, "post-10", 100L, "FREE", "제목", "본문", NOW);

        boolean written = service.writeComment(new Reaction(1L, post, null), List.of(), NOW);

        assertThat(written).isFalse();
        verify(commentService, never()).createComment(any(), any(), any(), any());
    }

    private void allowBudget() {
        given(sharedLimiter.acquire(anyList())).willReturn(new Decision(true, 0, null));
    }

    private static Member member() {
        return Member.builder().email("a1@snowthing.invalid").nickname("닉").build();
    }
}
