package com.ikae.snowthing.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.model.ResortTag;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final int BURST_REJECTED_ATTEMPT = 5;
    private static final int CONCURRENT_DUPLICATE_REQUESTS = 2;
    private static final long CONCURRENT_TEST_TIMEOUT_SECONDS = 10L;

    private ChatService chatService;
    private CustomUserDetails testUserDetails;
    private Member testMember;

    private ChatRecentHistoryBuffer chatRecentHistoryBuffer;

    @BeforeEach
    void setUp() {
        chatRecentHistoryBuffer = new ChatRecentHistoryBuffer();
        chatService = new ChatService(chatRecentHistoryBuffer);
        testMember =
                Member.builder()
                        .publicId("0191a1b2-c3d4-e5f6-a7b8-c9d0e1f2a3b4")
                        .email("snowrider@snowthing.org")
                        .nickname("몽블랑카버")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        ReflectionTestUtils.setField(testMember, "id", 100L);
        testUserDetails = new CustomUserDetails(testMember);
    }

    @Nested
    @DisplayName("정상 발송 테스트")
    class SuccessCases {

        @Test
        @DisplayName("리조트 태그를 포함한 슬로프 현황 메시지 발송 성공")
        void sendResortTaggedMessage_Success() {
            // given
            ChatMessageRequest request =
                    new ChatMessageRequest("PHOENIX", "챔피언 상단 시야 확 트였고 설질 엣지 짱짱하게 먹습니다! 대기 3분");

            // when
            ChatMessageResponse response =
                    chatService.processMessage(request, testUserDetails, "121.135.24.56");

            // then
            assertThat(response).isNotNull();
            assertThat(response.messageId()).isNotBlank();
            assertThat(response.sender().publicId()).isEqualTo(testMember.getPublicId());
            assertThat(response.sender().nickname()).isEqualTo(testMember.getNickname());
            assertThat(response.resortTag()).isEqualTo(ResortTag.PHOENIX.name());
            assertThat(response.content()).isEqualTo(request.content());
            assertThat(response.sentAt()).isNotBlank();
        }

        @Test
        @DisplayName("리조트 미선택(null) 시 일반 잡담 모드로 성공")
        void sendGeneralChatMessage_Success() {
            // given
            ChatMessageRequest request =
                    new ChatMessageRequest(null, "오늘 다들 어디로 출격하시나요? 날씨가 많이 춥네요.");

            // when
            ChatMessageResponse response =
                    chatService.processMessage(request, testUserDetails, "127.0.0.1");

            // then
            assertThat(response.resortTag()).isNull();
            assertThat(response.content()).isEqualTo("오늘 다들 어디로 출격하시나요? 날씨가 많이 춥네요.");
        }

        @Test
        @DisplayName("발송된 메시지는 최근 대화 버퍼에 자동으로 보관된다")
        void sentMessageStoredInRecentBuffer() {
            // given
            ChatMessageRequest request = new ChatMessageRequest("HIGH1", "아테나 슬로프 컨디션 좋습니다");

            // when
            ChatMessageResponse response =
                    chatService.processMessage(request, testUserDetails, "127.0.0.1");

            // then
            assertThat(chatService.getRecentMessages()).hasSize(1);
            assertThat(chatService.getRecentMessages().get(0).messageId())
                    .isEqualTo(response.messageId());
        }

        @Test
        @DisplayName("HTML 문자열은 원문으로 응답하고 React JSX 텍스트 경계에서 이스케이프한다")
        void htmlContent_keepsOriginalText() {
            // given
            ChatMessageRequest request =
                    new ChatMessageRequest(
                            null, "<script>alert('hack')</script> & \"hello\" 'world'");

            // when
            ChatMessageResponse response =
                    chatService.processMessage(request, testUserDetails, "127.0.0.1");

            // then
            assertThat(response.content()).isEqualTo(request.content());
        }
    }

    @Nested
    @DisplayName("컴플라이언스 및 유효성 실패 검증")
    class ValidationFailureCases {

        @Test
        @DisplayName("비로그인 유저가 발송을 시도하면 CHAT_UNAUTHORIZED 예외 발생")
        void unauthenticated_ThrowsException() {
            ChatMessageRequest request = new ChatMessageRequest(null, "안녕하세요");

            assertThatThrownBy(() -> chatService.processMessage(request, null, "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_UNAUTHORIZED);
        }

        @Test
        @DisplayName("공백 메시지 전송 시 CHAT_MESSAGE_EMPTY 예외 발생")
        void emptyContent_ThrowsException() {
            ChatMessageRequest request = new ChatMessageRequest(null, "    ");

            assertThatThrownBy(
                            () -> chatService.processMessage(request, testUserDetails, "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        @Test
        @DisplayName("100자를 초과하는 장문 전송 시 CHAT_MESSAGE_TOO_LONG 예외 발생")
        void tooLongContent_ThrowsException() {
            String longText = "가".repeat(101);
            ChatMessageRequest request = new ChatMessageRequest(null, longText);

            assertThatThrownBy(
                            () -> chatService.processMessage(request, testUserDetails, "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }

        @Test
        @DisplayName("외부 링크(HTTP/HTTPS/도메인) 전송 시 CHAT_EXTERNAL_LINK_FORBIDDEN 차단")
        void externalUrl_ThrowsException() {
            ChatMessageRequest request1 =
                    new ChatMessageRequest(null, "여기서 장비 싸게 파네요 https://bad-site.com");
            ChatMessageRequest request2 =
                    new ChatMessageRequest(null, "도박 사이트 접속 www.gamble.kr 가입");

            assertThatThrownBy(
                            () ->
                                    chatService.processMessage(
                                            request1, testUserDetails, "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);

            assertThatThrownBy(
                            () ->
                                    chatService.processMessage(
                                            request2, testUserDetails, "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);
        }

        @Test
        @DisplayName("고정 TLD 목록 밖의 도메인·IPv4·단축 URL도 외부 링크로 차단한다")
        void externalUrl_withoutProtocolOrKnownTld_throwsException() {
            List<String> forbiddenMessages =
                    List.of(
                            "거래는 dealer.dev 에서 해요",
                            "초대 주소 discord.gg/snowthing",
                            "상품은 shop.ai 에 있습니다",
                            "서버 주소 203.0.113.10:8080/path",
                            "단축 주소 bit.ly/snowthing");

            for (String content : forbiddenMessages) {
                ChatMessageRequest request = new ChatMessageRequest(null, content);
                assertThatThrownBy(
                                () ->
                                        chatService.processMessage(
                                                request, testUserDetails, "127.0.0.1"))
                        .isInstanceOf(CustomException.class)
                        .extracting("errorCode")
                        .isEqualTo(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);
            }
        }

        @Test
        @DisplayName("메신저 ID(텔레그램/오픈카톡/@아이디) 전송 시 CHAT_EXTERNAL_LINK_FORBIDDEN 차단")
        void messengerId_ThrowsException() {
            ChatMessageRequest request1 =
                    new ChatMessageRequest(null, "연락은 t.me/snow_dealer 로 주세요");
            ChatMessageRequest request2 =
                    new ChatMessageRequest(null, "디엠은 @secret_rider_99 로 부탁합니다");

            assertThatThrownBy(
                            () ->
                                    chatService.processMessage(
                                            request1, testUserDetails, "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);

            assertThatThrownBy(
                            () ->
                                    chatService.processMessage(
                                            request2, testUserDetails, "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);
        }

        @Test
        @DisplayName("5초 내 동일 메시지 연속 전송 시 CHAT_DUPLICATE_MESSAGE 차단 (공백/특수문자 변형 포함)")
        void duplicateMessageCooldown_ThrowsException() {
            // given: 첫 번째 메시지 전송 성공
            ChatMessageRequest firstRequest = new ChatMessageRequest(null, "용평 가시는 분 계신가요?");
            chatService.processMessage(firstRequest, testUserDetails, "127.0.0.1");

            // when & then: 5초 내 동일 내용(끝자리 점 찍거나 공백 변형) 재전송 시 차단
            ChatMessageRequest duplicateRequest = new ChatMessageRequest(null, "용평 가시는 분 계신가요?..");

            assertThatThrownBy(
                            () ->
                                    chatService.processMessage(
                                            duplicateRequest, testUserDetails, "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_DUPLICATE_MESSAGE);
        }

        @Test
        @DisplayName("1초 안에 서로 다른 메시지를 다섯 번 시도하면 CHAT_BURST_RATE_LIMIT으로 차단한다")
        void burstRateLimit_blocksFifthAttempt() {
            for (int attempt = 1; attempt < BURST_REJECTED_ATTEMPT; attempt++) {
                chatService.processMessage(
                        new ChatMessageRequest(null, "서로 다른 메시지 " + attempt),
                        testUserDetails,
                        "127.0.0.1");
            }

            assertThatThrownBy(
                            () ->
                                    chatService.processMessage(
                                            new ChatMessageRequest(null, "다섯 번째 메시지"),
                                            testUserDetails,
                                            "127.0.0.1"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CHAT_BURST_RATE_LIMIT);
            assertThat(chatRecentHistoryBuffer.getRecentMessages())
                    .hasSize(BURST_REJECTED_ATTEMPT - 1);
        }

        @Test
        @DisplayName("같은 회원의 동일 메시지 동시 요청은 하나만 성공한다")
        void duplicateMessage_concurrentRequests_allowOnlyOne() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_DUPLICATE_REQUESTS);
            CountDownLatch ready = new CountDownLatch(CONCURRENT_DUPLICATE_REQUESTS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ErrorCode>> results = new ArrayList<>();

            for (int index = 0; index < CONCURRENT_DUPLICATE_REQUESTS; index++) {
                results.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    try {
                                        chatService.processMessage(
                                                new ChatMessageRequest(null, "동시에 보낸 같은 메시지"),
                                                testUserDetails,
                                                "127.0.0.1");
                                        return null;
                                    } catch (CustomException exception) {
                                        return exception.getErrorCode();
                                    }
                                }));
            }

            assertThat(ready.await(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            try {
                List<ErrorCode> errorCodes =
                        results.stream()
                                .map(
                                        result -> {
                                            try {
                                                return result.get(
                                                        CONCURRENT_TEST_TIMEOUT_SECONDS,
                                                        TimeUnit.SECONDS);
                                            } catch (Exception exception) {
                                                throw new IllegalStateException(exception);
                                            }
                                        })
                                .toList();
                assertThat(errorCodes)
                        .containsExactlyInAnyOrder(null, ErrorCode.CHAT_DUPLICATE_MESSAGE);
                assertThat(chatRecentHistoryBuffer.getRecentMessages()).hasSize(1);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
