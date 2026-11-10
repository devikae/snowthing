package com.ikae.snowthing.domain.admin.log;

/** 관리자 조치 종류 (IKA-78). 문자열로 저장하므로 이름을 바꾸지 않는다. */
public enum AdminActionType {
    MEMBER_SUSPEND,
    MEMBER_REINSTATE,
    ADMIN_GRANT,
    ADMIN_REVOKE,
    POST_BLIND,
    POST_UNBLIND,
    POST_DELETE,
    COMMENT_BLIND,
    COMMENT_UNBLIND,
    COMMENT_DELETE,
    MARKET_MODERATION,
    RESORT_REPORT_MODERATION,
    CONTENT_REPORT_ACTION,
    BANNER_CREATE,
    BANNER_UPDATE,
    BANNER_DELETE,
    CHAT_IP_BAN,
    CHAT_IP_UNBAN,
    CONFIG_UPDATE
}
