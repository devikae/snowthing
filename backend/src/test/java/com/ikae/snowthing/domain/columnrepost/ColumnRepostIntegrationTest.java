package com.ikae.snowthing.domain.columnrepost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.LocalDateTime;
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

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.global.notification.DiscordNotifier;

/** 실제 PostService와 MySQL로 대기열 글이 순서대로 하나씩 올라가는지 본다. 디시 사진 받기와 디스코드만 대체한다. */
@SpringBootTest(
        properties = {
            "snowthing.column-repost.member-email=" + ColumnRepostIntegrationTest.EMAIL,
            "snowthing.column-repost.max-attempts=2",
            "snowthing.column-repost.link-base-url=https://snowthing.org",
            "spring.datasource.hikari.maximum-pool-size=2",
            // 클래스가 끝나면 컨텍스트를 닫는다. create-drop이면 닫힐 때 공유 테스트 스키마를 지워 다른 컨텍스트가 깨진다.
            "spring.jpa.hibernate.ddl-auto=update"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ColumnRepostIntegrationTest {

    static final String EMAIL = "column-repost-it@example.com";

    @Autowired private ColumnRepostService service;
    @Autowired private ColumnRepostRepository repository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private ColumnRepostImages images;
    @MockitoBean private DiscordNotifier notifier;

    private Member writer;

    @BeforeEach
    void setUp() throws IOException {
        categories
                .findByCode("INFO")
                .orElseGet(
                        () ->
                                categories.save(
                                        PostCategory.builder().code("INFO").name("정보게시판").build()));
        String unique = UUID.randomUUID().toString().substring(0, 8);
        memberRepository.findByEmail(EMAIL).ifPresent(stale -> deleteWriter(stale.getId()));
        writer =
                memberRepository.save(
                        Member.builder()
                                .email(EMAIL)
                                .nickname("휘" + unique)
                                .role(Role.ROLE_USER)
                                .build());
        jdbc.update("DELETE FROM column_repost_queue");
        given(images.replace(anyString(), anyString(), anyString()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM column_repost_queue");
        deleteWriter(writer.getId());
    }

    private void deleteWriter(Long memberId) {
        jdbc.update("DELETE FROM post WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
    }

    @Test
    @DisplayName("순번이 가장 앞선 대기 글 하나만 휘구리 계정으로 정보게시판에 올리고 알린다")
    void publishesNextInOrder() {
        save(2, 200L, "두 번째");
        save(1, 100L, "첫 번째");

        assertThat(service.publishNext()).isEqualTo(ColumnRepostService.Result.PUBLISHED);

        Map<String, Object> post =
                jdbc.queryForMap(
                        "SELECT p.title, p.content, c.code FROM post p JOIN post_category c"
                                + " ON c.category_id = p.category_id WHERE p.member_id = ?",
                        writer.getId());
        assertThat(post.get("title")).isEqualTo("첫 번째");
        assertThat(post.get("code")).isEqualTo("INFO");
        assertThat(statusOf(100L)).isEqualTo("PUBLISHED");
        assertThat(statusOf(200L)).isEqualTo("PENDING");
        verify(notifier).column(contains("휘구리 칼럼 1/2"));
        verify(notifier).column(contains("https://snowthing.org/posts/"));
    }

    @Test
    @DisplayName("두 번 실행하면 다음 글이 올라가고, 다 올라가면 아무것도 하지 않는다")
    void neverRepublishes() {
        save(1, 100L, "첫 번째");
        save(2, 200L, "두 번째");

        service.publishNext();
        service.publishNext();
        assertThat(service.publishNext()).isEqualTo(ColumnRepostService.Result.EMPTY);

        assertThat(
                        jdbc.queryForList(
                                "SELECT title FROM post WHERE member_id = ? ORDER BY post_id",
                                String.class,
                                writer.getId()))
                .containsExactly("첫 번째", "두 번째");
        verify(notifier, times(2)).column(anyString());
    }

    @Test
    @DisplayName("사진을 받지 못하면 그 글을 대기로 두고, 정해진 횟수가 차면 다음 글로 넘어가지 않고 멈춘다")
    void stopsAfterMaxAttempts() throws IOException {
        save(1, 100L, "첫 번째");
        save(2, 200L, "두 번째");
        reset(images);
        given(images.replace(anyString(), anyString(), anyString()))
                .willThrow(new IOException("timeout"));

        assertThat(service.publishNext()).isEqualTo(ColumnRepostService.Result.FAILED);
        assertThat(service.publishNext()).isEqualTo(ColumnRepostService.Result.FAILED);
        assertThat(service.publishNext()).isEqualTo(ColumnRepostService.Result.STOPPED);

        assertThat(statusOf(100L)).isEqualTo("PENDING");
        assertThat(statusOf(200L)).isEqualTo("PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM post WHERE member_id = ?",
                                Integer.class,
                                writer.getId()))
                .isZero();
        verify(notifier).alert(contains("1/2 (다음 게시 시각에 다시 시도)"));
        verify(notifier).alert(contains("2/2 (멈춤, 확인 필요)"));
        verify(notifier, never()).column(any());
    }

    @Test
    @DisplayName("이미 게시된 줄은 다시 고르지 않는다")
    void skipsPublishedRows() {
        save(1, 100L, "첫 번째");
        jdbc.update(
                "UPDATE column_repost_queue SET status = 'PUBLISHED', post_public_id = 'x'"
                        + " WHERE source_no = 100");

        assertThat(service.publishNext()).isEqualTo(ColumnRepostService.Result.EMPTY);
    }

    private void save(int sequence, long source, String title) {
        repository.save(
                new ColumnRepostItem(
                        sequence, source, title, title + " 본문입니다.", LocalDateTime.now()));
    }

    private String statusOf(long source) {
        return jdbc.queryForObject(
                "SELECT status FROM column_repost_queue WHERE source_no = ?", String.class, source);
    }
}
