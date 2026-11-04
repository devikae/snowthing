package com.ikae.snowthing.domain.answerdraft.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 답변 게시 기능 플래그. 기본값은 꺼짐이고 운영도 꺼진 채로 둔다. 켜도 사람이 관리자 세션에서 승인한 초안만 게시된다. 자동 게시는 없다.
 *
 * <p>환경 변수 {@code SNOWTHING_ANSWER_DRAFT_PUBLISH_ENABLED}로 바꾼다. 앱을 다시 띄워야 반영된다.
 */
@Component
public class AnswerDraftPublishPolicy {

    private boolean publishEnabled;

    public AnswerDraftPublishPolicy(
            @Value("${snowthing.answer-draft.publish-enabled:false}") boolean publishEnabled) {
        this.publishEnabled = publishEnabled;
    }

    public boolean isPublishEnabled() {
        return publishEnabled;
    }
}
