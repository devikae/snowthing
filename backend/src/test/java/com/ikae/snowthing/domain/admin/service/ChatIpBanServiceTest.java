package com.ikae.snowthing.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.ikae.snowthing.domain.admin.repository.ChatBannedIpRepository;
import com.ikae.snowthing.domain.chat.service.ChatRecentHistoryBuffer;

@ExtendWith(MockitoExtension.class)
class ChatIpBanServiceTest {

    @Mock private ChatBannedIpRepository repository;

    @Mock private ChatRecentHistoryBuffer recentHistoryBuffer;

    @Mock private SimpMessagingTemplate messagingTemplate;

    private ChatIpBanService chatIpBanService;

    @BeforeEach
    void setUp() {
        chatIpBanService = new ChatIpBanService(repository, recentHistoryBuffer, messagingTemplate);
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

        chatIpBanService.banIpByMessageId("msg-123", "admin");

        assertThat(chatIpBanService.isBanned("192.168.1.100")).isTrue();
    }
}
