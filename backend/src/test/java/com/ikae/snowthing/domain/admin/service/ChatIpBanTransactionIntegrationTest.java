package com.ikae.snowthing.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 메모리 차단 목록이 DB 커밋 결과를 따라가는지 실제 트랜잭션으로 확인한다 (IKA-93). */
@SpringBootTest
class ChatIpBanTransactionIntegrationTest {

    private static final String PREFIX = "2001:db8:93:";

    @Autowired private ChatIpBanService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        Long[] ids =
                jdbc.queryForList(
                                "SELECT id FROM chat_banned_ip WHERE banned_ip LIKE ?",
                                Long.class,
                                PREFIX + "%")
                        .toArray(Long[]::new);
        for (Long id : ids) service.unbanIp(id);
    }

    @Test
    @DisplayName("차단 트랜잭션이 롤백되면 메모리 차단도 남지 않는다")
    void rolledBackBanLeavesNoMemoryBan() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            service.banIp(
                                    PREFIX + "1::1",
                                    "롤백 확인",
                                    LocalDateTime.now().plusDays(1),
                                    "admin");
                            // 커밋 전에는 메모리에 반영되지 않는다.
                            assertThat(service.isBanned(PREFIX + "1::1")).isFalse();
                            status.setRollbackOnly();
                        });

        assertThat(service.isBanned(PREFIX + "1::1")).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM chat_banned_ip WHERE banned_ip LIKE ?",
                                Long.class,
                                PREFIX + "%"))
                .isZero();
    }

    @Test
    @DisplayName("커밋된 차단은 같은 /64 대역에 적용되고, 해제해도 다른 활성 차단이 남아 있으면 계속 막는다")
    void committedBanAndUnbanFollowDatabase() {
        var first =
                service.banIp(PREFIX + "2::1", "첫 차단", LocalDateTime.now().plusDays(1), "admin");
        var second =
                service.banIp(PREFIX + "2::2", "둘째 차단", LocalDateTime.now().plusDays(2), "admin");

        assertThat(service.isBanned(PREFIX + "2:aaaa:bbbb:cccc:dddd")).isTrue();

        service.unbanIp(first.id());
        assertThat(service.isBanned(PREFIX + "2::77")).isTrue();

        service.unbanIp(second.id());
        assertThat(service.isBanned(PREFIX + "2::77")).isFalse();
    }
}
