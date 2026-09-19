package com.ikae.snowthing.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "이메일 또는 비밀번호가 일치하지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_002", "해당 작업을 수행할 권한이 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "존재하지 않는 회원입니다."),
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "MEMBER_002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.BAD_REQUEST, "MEMBER_003", "이미 사용 중인 닉네임입니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_001", "존재하지 않거나 삭제된 게시글입니다."),
    POST_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_002", "존재하지 않는 게시판 카테고리입니다."),
    ALREADY_REACTED(HttpStatus.CONFLICT, "POST_003", "이미 추천 또는 비추천 투표를 완료한 게시글입니다."),
    INVALID_ANON_PASSWORD(HttpStatus.FORBIDDEN, "POST_004", "비회원 익명 비밀번호가 일치하지 않습니다."),
    INVALID_PAGE_LIMIT(
            HttpStatus.BAD_REQUEST,
            "POST_005",
            "게시판 조회의 최대 한계선은 100페이지(2,000개 글)까지입니다. 더 이전 글은 검색 기능을 이용해 주세요."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_001", "존재하지 않거나 이미 삭제된 댓글입니다."),
    PARENT_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_002", "존재하지 않는 부모 댓글입니다."),
    INVALID_COMMENT_PARENT(HttpStatus.BAD_REQUEST, "COMMENT_003", "동일한 게시글의 댓글에만 대댓글을 달 수 있습니다."),
    COMMENT_REPLY_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "COMMENT_004", "루트 댓글 1개당 작성 가능한 대댓글 수는 최대 100개입니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 입력값입니다."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "COMMON_002", "페이지 크기는 1 이상 100 이하이어야 합니다."),
    COMMENT_INVALID_PAGE_SIZE(
            HttpStatus.BAD_REQUEST, "COMMENT_005", "댓글 페이지 크기는 1 이상 50 이하이어야 합니다."),
    COMMENT_UPDATE_CONFLICT(
            HttpStatus.CONFLICT, "COMMENT_006", "다른 요청에서 댓글을 먼저 수정했습니다. 최신 댓글을 다시 확인해 주세요."),
    CHAT_EXTERNAL_LINK_FORBIDDEN(
            HttpStatus.BAD_REQUEST, "CHAT_001", "외부 링크 및 메신저 연락처는 전송할 수 없습니다."),
    CHAT_DUPLICATE_MESSAGE(
            HttpStatus.TOO_MANY_REQUESTS, "CHAT_002", "동일한 메시지를 연속으로 보낼 수 없습니다 (5초간 대기)."),
    CHAT_BURST_RATE_LIMIT(
            HttpStatus.TOO_MANY_REQUESTS, "CHAT_003", "메시지 전송 속도가 너무 빠릅니다. 잠시 후 다시 시도해 주세요."),
    CHAT_MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "CHAT_004", "공백 메시지는 전송할 수 없습니다."),
    CHAT_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "CHAT_005", "메시지는 최대 100자까지 작성할 수 있습니다."),
    CHAT_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "CHAT_006", "로그인 후 라이브톡에 참여할 수 있습니다."),
    CHAT_KILL_SWITCH_ACTIVE(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_007", "현재 라이브톡 점검 중입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
