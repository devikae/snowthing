package com.ikae.snowthing.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.admin.dto.ChatBannedIpResponse;
import com.ikae.snowthing.domain.admin.entity.ChatBannedIp;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.repository.ChatBannedIpRepository;
import com.ikae.snowthing.domain.chat.service.ChatRecentHistoryBuffer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * IP 차단 목록 (IKA-96). 저장소는 메모리 목록으로 흉내 내서, 차단과 해제 뒤 캐시가 DB를 다시 읽는 흐름을 그대로 본다. 트랜잭션 밖이라 바로 다시 읽는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatIpBanServiceTest {

    @Mock private ChatBannedIpRepository repository;
    @Mock private ChatRecentHistoryBuffer recentHistoryBuffer;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private AdminActionLogger actionLogger;

    private final List<ChatBannedIp> rows = new ArrayList<>();
    private ChatIpBanService service;

    @BeforeEach
    void setUp() {
        when(repository.findAllActiveBans(any()))
                .thenAnswer(
                        invocation -> {
                            LocalDateTime now = invocation.getArgument(0);
                            return rows.stream()
                                    .filter(
                                            row ->
                                                    row.getBannedUntil() == null
                                                            || row.getBannedUntil().isAfter(now))
                                    .toList();
                        });
        when(repository.save(any(ChatBannedIp.class)))
                .thenAnswer(
                        invocation -> {
                            ChatBannedIp row = invocation.getArgument(0);
                            ReflectionTestUtils.setField(row, "id", (long) rows.size() + 1);
                            rows.add(row);
                            return row;
                        });
        when(recentHistoryBuffer.removeByIpMatching(any())).thenReturn(List.of());
        service =
                new ChatIpBanService(
                        repository, recentHistoryBuffer, messagingTemplate, actionLogger);
        service.init();
    }

    @Test
    @DisplayName("IPv6 /64 차단은 같은 대역의 다른 호스트 주소를 모두 막고 옆 대역은 막지 않는다")
    void ipv6RangeBan() {
        ChatBannedIpResponse response =
                service.banIp(
                        "2001:fd8:c11e:2e00:1234::9/64",
                        "카지노 광고",
                        LocalDateTime.now().plusDays(30),
                        "admin");

        assertThat(response.bannedIp()).isEqualTo("2001:fd8:c11e:2e00::/64");
        assertThat(service.isBanned("2001:fd8:c11e:2e00:aaaa:bbbb:cccc:dddd")).isTrue();
        assertThat(service.isBanned("2001:fd8:c11e:2e00::1")).isTrue();
        assertThat(service.isBanned("2001:fd8:c11e:2e01::1")).isFalse();
    }

    @Test
    @DisplayName("기존 단일 IP 차단 줄도 시작할 때 읽어 계속 막는다")
    void legacySingleIpRowStillWorks() {
        rows.add(row("10.20.30.40", LocalDateTime.now().plusDays(1)));
        service.init();

        assertThat(service.isBanned("10.20.30.40")).isTrue();
        assertThat(service.isBanned("10.20.30.41")).isFalse();
    }

    @Test
    @DisplayName("같은 IP에 차단이 겹치면 가장 늦게 끝나는 차단을 따르고, 한 줄을 풀면 남은 줄로 다시 계산한다")
    void overlappingBansKeepLaterExpiry() {
        service.banIp("203.0.113.0/24", "긴 차단", LocalDateTime.now().plusDays(30), "admin");
        service.banIp("203.0.113.7", "1일 차단", LocalDateTime.now().plusDays(1), "admin");
        service.banIp("203.0.113.8", "이미 끝난 차단", LocalDateTime.now().minusSeconds(1), "admin");
        // 끝난 짧은 차단이 있어도 긴 대역 차단이 남아 있으면 계속 막는다.
        assertThat(service.isBanned("203.0.113.8")).isTrue();

        ChatBannedIp longBan = rows.get(0);
        when(repository.findById(longBan.getId())).thenReturn(Optional.of(longBan));
        rows.remove(longBan);
        service.unbanIp(longBan.getId());

        assertThat(service.isBanned("203.0.113.7")).isTrue();
        assertThat(service.isBanned("203.0.113.8")).isFalse();
    }

    @Test
    @DisplayName("만료된 차단은 막지 않는다")
    void expiredBanDoesNotBlock() {
        rows.add(row("198.51.100.0/24", LocalDateTime.now().plusSeconds(1)));
        service.init();
        ReflectionTestUtils.setField(
                rows.get(0), "bannedUntil", LocalDateTime.now().minusSeconds(1));
        service.init();

        assertThat(service.isBanned("198.51.100.9")).isFalse();
    }

    @Test
    @DisplayName("작성 차단은 대역 정보 없이 일반 오류로 막는다")
    void requireWritableUsesGenericError() {
        service.banIp("198.51.100.0/24", "도배", null, "admin");

        assertThatThrownBy(() -> service.requireWritable("198.51.100.200"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WRITE_NOT_ALLOWED);
        service.requireWritable("198.51.101.1");
    }

    @Test
    @DisplayName("형식이 틀리거나 너무 넓은 대역은 저장하지 않는다")
    void rejectsInvalidOrTooWide() {
        assertThatThrownBy(() -> service.banIp("not-an-ip", "x", null, "admin"))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IP_BAN_INVALID_RANGE);
        assertThatThrownBy(() -> service.banIp("10.0.0.0/8", "x", null, "admin"))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IP_BAN_RANGE_TOO_WIDE);
        assertThatThrownBy(() -> service.banIp("2001:db8::/16", "x", null, "admin"))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IP_BAN_RANGE_TOO_WIDE);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("보류 글 차단 단축 버튼: IPv6은 /64, IPv4는 주소 하나를 막는다")
    void writerRangeShortcut() {
        assertThat(service.banWriterRange("2001:db8:1:2:3:4:5:6", "보류", null, "admin").bannedIp())
                .isEqualTo("2001:db8:1:2::/64");
        assertThat(service.banWriterRange("203.0.113.9", "보류", null, "admin").bannedIp())
                .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("라이브톡 메시지 차단은 받은 기간만큼 막고 그 IP의 최근 메시지를 지운다")
    void banIpByMessageIdUsesDays() {
        when(recentHistoryBuffer.getIpForMessage("msg-123")).thenReturn("192.168.1.100");
        when(recentHistoryBuffer.removeByIpMatching(any()))
                .thenReturn(List.of("msg-123", "msg-124"));

        service.banIpByMessageId("msg-123", 30, "admin");

        assertThat(service.isBanned("192.168.1.100")).isTrue();
        assertThat(rows.get(0).getBannedUntil())
                .isAfter(LocalDateTime.now().plusDays(29))
                .isBefore(LocalDateTime.now().plusDays(31));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(any(String.class), payload.capture());
        assertThat(payload.getValue().toString()).contains("msg-123", "msg-124");
    }

    private static ChatBannedIp row(String ip, LocalDateTime until) {
        return ChatBannedIp.builder()
                .bannedIp(ip)
                .reason("기존")
                .bannedUntil(until)
                .createdBy("admin")
                .build();
    }
}
