package com.ikae.snowthing.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.event.MemberSignedUpEvent;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.global.notification.DailySummaryQueryRepository.Split;

/** 실제 트랜잭션과 MySQL로 가입 알림의 커밋 의존성과 일일 요약 집계를 본다. Discord 전송만 대체한다. */
@SpringBootTest(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=2",
            // 클래스가 끝나면 컨텍스트를 닫는다. create-drop이면 닫힐 때 공유 테스트 스키마를 지워 다른 컨텍스트가 깨진다.
            "spring.jpa.hibernate.ddl-auto=update"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DiscordNotificationIntegrationTest {

    // 다른 테스트 데이터와 겹치지 않는 날짜
    private static final LocalDateTime DAY_START = LocalDateTime.of(2001, 2, 3, 0, 0);
    private static final LocalDateTime DAY_END = DAY_START.plusDays(1);

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private DailySummaryQueryRepository queryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private DiscordNotifier notifier;

    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM post WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("가입 트랜잭션이 커밋되면 알림을 보낸다")
    void notifiesAfterCommit() {
        transactionTemplate.executeWithoutResult(
                status -> eventPublisher.publishEvent(new MemberSignedUpEvent("커밋보더", "EMAIL")));

        verify(notifier).event(startsWith("🎉 신규 가입: 커밋보더 (EMAIL) "));
    }

    @Test
    @DisplayName("가입 트랜잭션이 롤백되면 알림을 보내지 않는다")
    void skipsOnRollback() {
        transactionTemplate.executeWithoutResult(
                status -> {
                    eventPublisher.publishEvent(new MemberSignedUpEvent("롤백보더", "EMAIL"));
                    status.setRollbackOnly();
                });

        verify(notifier, never()).event(anyString());
    }

    @Test
    @DisplayName("집계 구간 안의 가입, 글을 세고 AI 계정을 구분한다")
    void countsDayWithAiSplit() {
        Long human = saveMember("rider@example.com", DAY_START);
        Long ai = saveMember("ai@snowthing.invalid", DAY_START.plusHours(9));
        saveMember("late@example.com", DAY_END);
        Long categoryId =
                categories
                        .findByCode("FREE")
                        .orElseGet(
                                () ->
                                        categories.save(
                                                PostCategory.builder()
                                                        .code("FREE")
                                                        .name("FREE")
                                                        .build()))
                        .getId();
        insertPost(human, categoryId, DAY_START.plusHours(1), true);
        insertPost(ai, categoryId, DAY_START.plusHours(2), false);
        insertPost(ai, categoryId, DAY_END.minusSeconds(1), false);
        insertPost(human, categoryId, DAY_END, false);

        assertThat(queryRepository.countHumanSignUps(DAY_START, DAY_END)).isEqualTo(1);
        assertThat(queryRepository.countPosts(DAY_START, DAY_END)).isEqualTo(new Split(1, 2));
        assertThat(queryRepository.countComments(DAY_START, DAY_END)).isEqualTo(new Split(0, 0));
    }

    private Long saveMember(String email, LocalDateTime createdAt) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Long id =
                memberRepository
                        .save(
                                Member.builder()
                                        .email(unique + "-" + email)
                                        .nickname("n" + unique)
                                        .role(Role.ROLE_USER)
                                        .build())
                        .getId();
        memberIds.add(id);
        jdbc.update("UPDATE member SET created_at = ? WHERE member_id = ?", createdAt, id);
        return id;
    }

    /** 삭제된 글도 그날 작성 수에 들어간다. */
    private void insertPost(
            Long memberId, Long categoryId, LocalDateTime createdAt, boolean deleted) {
        jdbc.update(
                "INSERT INTO post (public_id, member_id, category_id, title, content, writer_ip,"
                        + " is_anonymous, view_count, comment_count, like_count, dislike_count,"
                        + " has_image, status, is_deleted, content_version, created_at, updated_at)"
                        + " VALUES (?, ?, ?, '제목', '본문', '58.120.1.1', false, 0, 0, 0, 0,"
                        + " false, 'NORMAL', ?, 0, ?, ?)",
                UUID.randomUUID().toString(),
                memberId,
                categoryId,
                deleted,
                createdAt,
                createdAt);
    }
}
