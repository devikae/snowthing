package com.ikae.snowthing.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.admin.dto.ChatBannedIpResponse;
import com.ikae.snowthing.domain.admin.entity.ChatBannedIp;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.repository.ChatBannedIpRepository;
import com.ikae.snowthing.domain.chat.service.ChatRecentHistoryBuffer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class ChatIpBanServiceTest {

    @Mock private ChatBannedIpRepository repository;

    @Mock private ChatRecentHistoryBuffer recentHistoryBuffer;

    @Mock private SimpMessagingTemplate messagingTemplate;

    @Mock private AdminActionLogger actionLogger;

    private ChatIpBanService chatIpBanService;

    @BeforeEach
    void setUp() {
        chatIpBanService =
                new ChatIpBanService(
                        repository, recentHistoryBuffer, messagingTemplate, actionLogger);
    }

    @Test
    @DisplayName("IP 차단 시 캐시에 등록되고 저장이 수행된다")
    void banIp_success() {
        ChatBannedIp entity =
                ChatBannedIp.builder()
                        .bannedIp("127.0.0.1")
                        .reason("도배")
                        .bannedUntil(LocalDateTime.now().plusDays(1))
                        .createdBy("admin")
                        .build();

        when(repository.save(any(ChatBannedIp.class))).thenReturn(entity);

        ChatBannedIpResponse res =
                chatIpBanService.banIp("127.0.0.1", "도배", LocalDateTime.now().plusDays(1), "admin");

        assertThat(res.bannedIp()).isEqualTo("127.0.0.1");
        assertThat(chatIpBanService.isBanned("127.0.0.1")).isTrue();
    }

    @Test
    @DisplayName("IP 차단 해제 시 캐시와 DB에서 제거된다")
    void unbanIp_success() {
        ChatBannedIp entity = ChatBannedIp.builder().bannedIp("127.0.0.1").reason("도배").build();
        ReflectionTestUtils.setField(entity, "id", 1L);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        chatIpBanService.unbanIp(1L);

        verify(repository).delete(entity);
        assertThat(chatIpBanService.isBanned("127.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("원클릭 메시지 ID 차단 시 버퍼에서 IP를 찾아 차단하고 삭제 메시지를 브로드캐스트한다")
    void banIpByMessageId_success() {
        when(recentHistoryBuffer.getIpForMessage("msg-123")).thenReturn("192.168.1.100");
        when(recentHistoryBuffer.removeByIp("192.168.1.100"))
                .thenReturn(List.of("msg-123", "msg-124"));
        when(repository.save(any(ChatBannedIp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        chatIpBanService.banIpByMessageId("msg-123", 7, "admin");

        assertThat(chatIpBanService.isBanned("192.168.1.100")).isTrue();
    }

    @Test
    @DisplayName("IPv6는 같은 /64 대역이면 주소 끝자리가 달라도 차단된다")
    void ipv6BanCoversSame64() {
        when(repository.save(any(ChatBannedIp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        chatIpBanService.banIp("2001:db8:1:2::10", "도배", LocalDateTime.now().plusDays(1), "admin");

        assertThat(chatIpBanService.isBanned("2001:db8:1:2:abcd:ef01:2345:6789")).isTrue();
        assertThat(chatIpBanService.isBanned("2001:0db8:0001:0002:0000:0000:0000:0001")).isTrue();
        assertThat(chatIpBanService.isBanned("2001:db8:1:3::10")).isFalse();
    }

    @Test
    @DisplayName("예전에 원문으로 저장된 차단 행도 시작 시 정규화해 같은 /64 대역을 막는다")
    void legacyRawRowsAreNormalizedOnLoad() {
        when(repository.findAllActiveBans(any()))
                .thenReturn(
                        List.of(
                                ChatBannedIp.builder()
                                        .bannedIp("2001:db8:aa:bb::1")
                                        .reason("도배")
                                        .createdBy("admin")
                                        .build(),
                                ChatBannedIp.builder()
                                        .bannedIp(" 203.0.113.7 ")
                                        .reason("도배")
                                        .createdBy("admin")
                                        .build()));

        chatIpBanService.init();

        assertThat(chatIpBanService.isBanned("2001:db8:aa:bb:1111:2222:3333:4444")).isTrue();
        assertThat(chatIpBanService.isBanned("203.0.113.7")).isTrue();
        assertThat(chatIpBanService.isBanned("203.0.113.8")).isFalse();
    }

    @Test
    @DisplayName("해제해도 같은 대역의 다른 활성 차단이 있으면 계속 막는다")
    void unbanKeepsOtherActiveBanOfSameKey() {
        ChatBannedIp first =
                ChatBannedIp.builder()
                        .bannedIp("2001:db8:1:2::1")
                        .reason("도배")
                        .bannedUntil(LocalDateTime.now().plusDays(1))
                        .createdBy("admin")
                        .build();
        ReflectionTestUtils.setField(first, "id", 1L);
        ChatBannedIp second =
                ChatBannedIp.builder()
                        .bannedIp("2001:db8:1:2::2")
                        .reason("도배")
                        .bannedUntil(LocalDateTime.now().plusDays(3))
                        .createdBy("admin")
                        .build();
        ReflectionTestUtils.setField(second, "id", 2L);
        when(repository.findAllActiveBans(any())).thenReturn(List.of(first, second));
        chatIpBanService.init();
        when(repository.findById(1L)).thenReturn(Optional.of(first));
        when(repository.findAllActiveBans(any())).thenReturn(List.of(second));

        chatIpBanService.unbanIp(1L);

        assertThat(chatIpBanService.isBanned("2001:db8:1:2::99")).isTrue();

        when(repository.findById(2L)).thenReturn(Optional.of(second));
        when(repository.findAllActiveBans(any())).thenReturn(List.of());
        chatIpBanService.unbanIp(2L);

        assertThat(chatIpBanService.isBanned("2001:db8:1:2::99")).isFalse();
    }

    @Test
    @DisplayName("메시지의 IP가 IP가 아닌 값이면 차단하지 않고 그 메시지만 지운다")
    void banByMessageWithNonIpOnlyRemovesMessage() {
        when(recentHistoryBuffer.getIpForMessage("msg-9")).thenReturn("unknown");
        when(recentHistoryBuffer.removeByMessageId("msg-9")).thenReturn(List.of("msg-9"));

        chatIpBanService.banIpByMessageId("msg-9", 7, "admin");

        verify(repository, never()).save(any());
        verify(recentHistoryBuffer).removeByMessageId("msg-9");
    }

    @Test
    @DisplayName("같은 /64에 짧은 차단이 뒤에 와도 긴 차단을 따른다")
    void laterExpiryWinsWithinSame64() {
        when(repository.save(any(ChatBannedIp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        chatIpBanService.banIp("2001:db8:5:6::1", "도배", LocalDateTime.now().plusDays(30), "admin");
        chatIpBanService.banIp(
                "2001:db8:5:6::2", "도배", LocalDateTime.now().minusMinutes(1), "admin");

        assertThat(chatIpBanService.isBanned("2001:db8:5:6::99")).isTrue();
    }

    @Test
    @DisplayName("IP가 아닌 값은 차단하지 않는다")
    void nonIpBanIsRejected() {
        assertThatThrownBy(
                        () ->
                                chatIpBanService.banIp(
                                        "not-an-ip",
                                        "도배",
                                        LocalDateTime.now().plusDays(1),
                                        "admin"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        failure ->
                                assertThat(failure.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT));
        verify(repository, never()).save(any());
        assertThat(chatIpBanService.isBanned("unknown")).isFalse();
    }
}
