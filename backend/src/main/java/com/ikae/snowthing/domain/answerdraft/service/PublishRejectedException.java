package com.ikae.snowthing.domain.answerdraft.service;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.Getter;

/** 게시 직전 검사에서 거절됨. 게시 트랜잭션을 되돌린 뒤 바깥에서 감사 기록을 남기려고 초안 ID를 함께 들고 나온다. */
@Getter
class PublishRejectedException extends CustomException {

    private final Long draftId;

    PublishRejectedException(Long draftId, ErrorCode errorCode) {
        super(errorCode);
        this.draftId = draftId;
    }
}
