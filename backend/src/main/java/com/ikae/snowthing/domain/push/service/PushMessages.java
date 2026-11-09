package com.ikae.snowthing.domain.push.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ikae.snowthing.domain.notification.entity.NotificationType;
import com.ikae.snowthing.domain.notification.event.NotificationsCreatedEvent.Item;

/**
 * 알림 한 건을 푸시 본문으로 바꾼다. 푸시는 잠금 화면에 그대로 뜨므로 작성자 이름, 댓글 미리보기, 조치 사유 같은 내용은 넣지 않고 종류만 알린다.
 *
 * <p>이동 경로는 화면의 알림 목록({@code notificationHref})과 같은 규칙이다. 운영 조치 안내는 글이 가려졌거나 지워졌을 수 있어 알림 목록으로 보낸다.
 */
final class PushMessages {

    static final String TITLE = "눈팅";
    static final String NOTIFICATIONS_URL = "/notifications";

    private PushMessages() {}

    static Map<String, String> of(Item item) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("title", TITLE);
        message.put("body", body(item));
        message.put("url", url(item));
        return message;
    }

    static String body(Item item) {
        return switch (item.type()) {
            case POST_COMMENT -> "내 글에 새 댓글이 달렸어요";
            case COMMENT_REPLY -> "내 댓글에 새 답글이 달렸어요";
            case INQUIRY_ANSWER -> "문의에 답변이 달렸어요";
            case MODERATION_RESULT -> "운영팀 안내가 도착했어요";
        };
    }

    static String url(Item item) {
        if (item.postPublicId() == null || item.type() == NotificationType.MODERATION_RESULT) {
            return NOTIFICATIONS_URL;
        }
        String base =
                switch (String.valueOf(item.postCategoryCode())) {
                    case "MARKET" -> "/market";
                    case "CARPOOL" -> "/carpool";
                    default -> "/posts";
                };
        return base
                + "/"
                + URLEncoder.encode(item.postPublicId(), StandardCharsets.UTF_8)
                + "#comments";
    }
}
