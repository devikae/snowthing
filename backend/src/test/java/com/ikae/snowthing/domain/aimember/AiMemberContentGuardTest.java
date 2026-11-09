package com.ikae.snowthing.domain.aimember;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.CommentView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.PostView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.Reaction;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.ComposeRequest;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.ComposeResponse;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.Line;
import com.ikae.snowthing.domain.aimember.persona.Persona;
import com.ikae.snowthing.domain.aimember.persona.Persona.Activity;
import com.ikae.snowthing.domain.aimember.persona.Persona.Window;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.dto.SenderDto;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.config.AiMemberProperties;
import com.ikae.snowthing.global.security.SharedRequestLimiter;
import com.ikae.snowthing.global.security.SharedRequestLimiter.Decision;

/** AI 멤버의 쓰기 경로(글, 댓글, 채팅)를 하나씩 지나며 정치 글과 비슷한 글이 저장되지 않는지 본다. */
@ExtendWith(MockitoExtension.class)
class AiMemberContentGuardTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 20, 19, 0);
    private static final AiMember MEMBER =
            new AiMember(
                    new Persona(
                            "a1",
                            "a1@snowthing.invalid",
                            "닉",
                            "211.36.10.20",
                            "설명",
                            new Activity(
                                    List.of(new Window(18, 24)),
                                    1,
                                    Map.of("QNA", 1),
                                    List.of("부츠 끈이 자꾸 풀림"))),
                    1L,
                    "public-1");
    private static final PostView POST =
            new PostView(10L, "post-10", 100L, "FREE", "부츠 추천 좀", "발볼 넓어요", NOW);

    @Mock private AiComposeGateway gateway;
    @Mock private AiMemberQueryRepository queryRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PostService postService;
    @Mock private CommentService commentService;
    @Mock private ChatService chatService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private SharedRequestLimiter sharedLimiter;
    @Mock private ScheduledExecutorService executor;

    private AiMemberRoster roster;
    private AiMemberActionService service;

    @BeforeEach
    void setUp() {
        roster = new AiMemberRoster();
        roster.replace(List.of(MEMBER));
        service =
                new AiMemberActionService(
                        new AiMemberProperties(true, "personas.yml", 100, List.of("대통령", "왁싱"), null),
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
        lenient().when(sharedLimiter.acquire(anyList())).thenReturn(new Decision(true, 0, null));
        lenient()
                .when(memberRepository.findStatusById(any()))
                .thenReturn(Optional.of(MemberStatus.ACTIVE));
    }

    @Test
    @DisplayName("글: 제목이나 본문에 정치 얘기가 있으면 저장하지 않는다")
    void dropsPoliticalPost() {
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", "대통령 얘기", "본문", null));
        service.writePost(MEMBER, NOW);

        given(gateway.compose(any()))
                .willReturn(new ComposeResponse("r", "제목", "다음 선거 누구 뽑음", null));
        service.writePost(MEMBER, NOW);

        verify(postService, never()).createPost(any(), any(), any());
    }

    @Test
    @DisplayName("댓글: 생성된 댓글에 정치 얘기가 있으면 저장하지 않는다")
    void dropsPoliticalComment() {
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", null, "정부가 문제임", null));

        boolean written = service.writeComment(new Reaction(1L, POST, null), List.of(), NOW);

        assertThat(written).isFalse();
        verify(commentService, never()).createComment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("댓글: 글이나 댓글에 정치 얘기가 있으면 모델을 부르지 않고 응하지 않는다")
    void doesNotAnswerPoliticalThread() {
        PostView political =
                new PostView(11L, "post-11", 100L, "FREE", "이번 대선 어떻게 봄", "보드 얘기 아님", NOW);
        CommentView politicalComment =
                new CommentView(1L, 10L, 100L, null, "좌파가 문제야 넌 어떻게 생각함", false, NOW);

        assertThat(service.writeComment(new Reaction(1L, political, null), List.of(), NOW))
                .isFalse();
        assertThat(service.writeComment(new Reaction(1L, POST, 1L), List.of(politicalComment), NOW))
                .isFalse();

        verifyNoInteractions(gateway);
        verify(commentService, never()).createComment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("채팅: 한 줄이라도 정치 얘기면 대화 전체를 보내지 않는다")
    void dropsWholeChatWhenAnyLineIsPolitical() {
        given(chatService.getRecentMessages()).willReturn(List.of());
        given(gateway.compose(any()))
                .willReturn(
                        new ComposeResponse(
                                "r",
                                null,
                                null,
                                List.of(new Line("a1", "오늘 눈 옴"), new Line("a1", "탄핵 얘기 봤음?"))));

        service.chat(List.of(MEMBER), 2, NOW);

        verifyNoInteractions(executor);
    }

    @Test
    @DisplayName("채팅: 사용자의 정치 발언은 모델 입력에서 뺀다")
    void removesPoliticalMessagesFromChatContext() {
        given(chatService.getRecentMessages())
                .willReturn(
                        List.of(
                                message("m1", "public-9", "대통령 어떻게 생각해?"),
                                message("m2", "public-9", "오늘 곤지암 사람 많음?")));
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", null, null, List.of()));

        service.chat(List.of(MEMBER), 1, NOW);

        assertThat(composed().recentChat())
                .extracting(AiComposeGateway.Message::content)
                .containsExactly("오늘 곤지암 사람 많음?");
    }

    @Test
    @DisplayName("채팅 반응: 마지막 메시지가 정치 얘기면 받아주지 않는다")
    void doesNotReactToPoliticalChat() {
        AiMemberActionService actions = org.mockito.Mockito.mock(AiMemberActionService.class);
        AiActivityScheduler scheduler =
                new AiActivityScheduler(
                        roster, queryRepository, actions, chatService, executor, Clock.systemUTC());
        given(chatService.getRecentMessages())
                .willReturn(List.of(message("m1", "public-9", "정부 욕 좀 같이 하자")));

        scheduler.runDue(NOW.plusHours(1));

        verify(actions, never()).chat(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("활동 시간이 아닌 회원만 있으면 아무것도 하지 않는다")
    void staysQuietOutsideActiveHours() {
        AiMemberActionService actions = org.mockito.Mockito.mock(AiMemberActionService.class);
        AiActivityScheduler scheduler =
                new AiActivityScheduler(
                        roster, queryRepository, actions, chatService, executor, Clock.systemUTC());

        scheduler.runDue(NOW.withHour(10));

        verifyNoInteractions(actions, queryRepository, chatService);
    }

    @Test
    @DisplayName("사이트 데이터의 정치 문구도 모델에 넘기지 않는다")
    void removesPoliticsFromSiteContext() {
        given(queryRepository.findRecentSnowReports(any()))
                .willReturn(List.of("곤지암: 정설 좋음", "곤지암: 정권 바뀌면 리프트값 내림"));
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", null, null, null));

        service.writePost(MEMBER, NOW);

        assertThat(composed().context().snowReports()).containsExactly("곤지암: 정설 좋음");
        assertThat(composed().context().topicKeywords()).containsExactly("왁싱");
    }

    @Test
    @DisplayName("글: 페르소나의 게시판 비중과 소재로 요청을 만든다")
    void usesPersonaBoardAndTopic() {
        given(memberRepository.findById(1L))
                .willReturn(
                        Optional.of(
                                Member.builder()
                                        .email("a1@snowthing.invalid")
                                        .nickname("닉")
                                        .build()));
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", "끈 풀림", "왜 이럼", null));

        service.writePost(MEMBER, NOW);

        ComposeRequest request = composed();
        assertThat(request.board()).isEqualTo("QNA");
        assertThat(request.brief().topic()).isEqualTo("부츠 끈이 자꾸 풀림");
        assertThat(request.brief().form()).isNotBlank();
        ArgumentCaptor<PostCreateRequest> saved = ArgumentCaptor.forClass(PostCreateRequest.class);
        verify(postService).createPost(saved.capture(), any(), any());
        assertThat(saved.getValue().categoryCode()).isEqualTo("QNA");
    }

    @Test
    @DisplayName("글: 최근 제목이나 AI 글과 지나치게 비슷하면 저장하지 않는다")
    void dropsTooSimilarPost() {
        given(queryRepository.findTitlesSince(any(), any()))
                .willReturn(List.of("개장 초반 리프트 대기 줄 어느 시간대가 낫나요"));
        given(gateway.compose(any()))
                .willReturn(new ComposeResponse("r", "개장 초반 리프트 대기 줄 어느 시간대가 좋나요", "본문", null));
        service.writePost(MEMBER, NOW);

        given(queryRepository.findRecentContents(any(), any()))
                .willReturn(List.of("공지에서 오픈 면수만 보지 말고 리프트 동선이랑 정설 여부까지 봐야 함"));
        given(gateway.compose(any()))
                .willReturn(
                        new ComposeResponse(
                                "r", "전혀 다른 제목", "공지에서 오픈 면수만 보지 말고 리프트 동선이랑 정설 여부까지 봐야 해요", null));
        service.writePost(MEMBER, NOW);

        verify(postService, never()).createPost(any(), any(), any());
    }

    @Test
    @DisplayName("댓글: 짧은 맞장구는 최근 댓글과 같아도 저장한다")
    void keepsShortRepeatedComment() {
        given(memberRepository.findById(1L))
                .willReturn(
                        Optional.of(
                                Member.builder()
                                        .email("a1@snowthing.invalid")
                                        .nickname("닉")
                                        .build()));
        given(gateway.compose(any())).willReturn(new ComposeResponse("r", null, "ㅇㅈ", null));

        boolean written = service.writeComment(new Reaction(1L, POST, null), List.of(), NOW);

        assertThat(written).isTrue();
        verify(queryRepository, never()).findRecentContents(any(), any());
    }

    private ComposeRequest composed() {
        ArgumentCaptor<ComposeRequest> request = ArgumentCaptor.forClass(ComposeRequest.class);
        verify(gateway).compose(request.capture());
        return request.getValue();
    }

    private static ChatMessageResponse message(String id, String sender, String content) {
        return new ChatMessageResponse(
                id, new SenderDto(sender, "회원"), null, content, NOW.toString());
    }
}
