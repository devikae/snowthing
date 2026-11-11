package com.ikae.snowthing.domain.admin.log;

/** 조치 대상 종류. 회원과 글은 공개 ID, 나머지는 번호나 키를 target_id에 둔다. */
public enum AdminTargetType {
    MEMBER,
    POST,
    COMMENT,
    RESORT_REPORT,
    CONTENT_REPORT,
    BANNER,
    CHAT_BAN,
    CONFIG
}
