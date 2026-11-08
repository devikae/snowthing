package com.ikae.snowthing.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.comment.event.CommentCreatedEvent;
import com.ikae.snowthing.domain.notification.entity.NotificationType;

class NotificationRecipientsTest {

    private static final Long POST_AUTHOR = 1L;
    private static final Long PARENT_AUTHOR = 2L;
    private static final Long ACTOR = 3L;

    @Test
    @DisplayName("일반 댓글은 글 작성자에게 POST_COMMENT")
    void rootComment() {
        assertThat(resolve("FREE", POST_AUTHOR, null, ACTOR, false))
                .containsExactly(Map.entry(POST_AUTHOR, NotificationType.POST_COMMENT));
    }

    @Test
    @DisplayName("답글은 부모 댓글 작성자에게 COMMENT_REPLY, 글 작성자에게 POST_COMMENT")
    void reply() {
        assertThat(resolve("FREE", POST_AUTHOR, PARENT_AUTHOR, ACTOR, false))
                .containsExactly(
                        Map.entry(PARENT_AUTHOR, NotificationType.COMMENT_REPLY),
                        Map.entry(POST_AUTHOR, NotificationType.POST_COMMENT));
    }

    @Test
    @DisplayName("글 작성자가 부모 댓글 작성자이면 COMMENT_REPLY 하나만")
    void replyDeduplicated() {
        assertThat(resolve("FREE", POST_AUTHOR, POST_AUTHOR, ACTOR, false))
                .containsExactly(Map.entry(POST_AUTHOR, NotificationType.COMMENT_REPLY));
    }

    @Test
    @DisplayName("본인 글의 본인 댓글, 본인 댓글의 본인 답글은 알림이 없다")
    void selfIsExcluded() {
        assertThat(resolve("FREE", ACTOR, null, ACTOR, false)).isEmpty();
        assertThat(resolve("FREE", ACTOR, ACTOR, ACTOR, false)).isEmpty();
        assertThat(resolve("FREE", POST_AUTHOR, ACTOR, ACTOR, false))
                .containsExactly(Map.entry(POST_AUTHOR, NotificationType.POST_COMMENT));
    }

    @Test
    @DisplayName("비회원 글과 비회원 댓글에는 받는 사람이 없다")
    void guestHasNoReceiver() {
        assertThat(resolve("FREE", null, null, ACTOR, false)).isEmpty();
        assertThat(resolve("FREE", POST_AUTHOR, null, null, false))
                .containsExactly(Map.entry(POST_AUTHOR, NotificationType.POST_COMMENT));
    }

    @Test
    @DisplayName("1:1 문의에 관리자가 답하면 문의 작성자에게 INQUIRY_ANSWER가 우선한다")
    void inquiryAnswer() {
        assertThat(resolve("INQUIRY", POST_AUTHOR, null, ACTOR, true))
                .containsExactly(Map.entry(POST_AUTHOR, NotificationType.INQUIRY_ANSWER));
        assertThat(resolve("INQUIRY", POST_AUTHOR, POST_AUTHOR, ACTOR, true))
                .containsExactly(Map.entry(POST_AUTHOR, NotificationType.INQUIRY_ANSWER));
    }

    @Test
    @DisplayName("관리자 댓글이라도 문의 글이 아니면 일반 규칙을 따른다")
    void adminOnNormalPost() {
        assertThat(resolve("FREE", POST_AUTHOR, null, ACTOR, true))
                .containsExactly(Map.entry(POST_AUTHOR, NotificationType.POST_COMMENT));
    }

    private static Map<Long, NotificationType> resolve(
            String category, Long postAuthor, Long parentAuthor, Long actor, boolean admin) {
        return NotificationRecipients.resolve(
                new CommentCreatedEvent(
                        10L,
                        20L,
                        "post-public-id",
                        category,
                        postAuthor,
                        parentAuthor,
                        actor,
                        admin,
                        false,
                        "작성자",
                        "미리보기"));
    }
}
