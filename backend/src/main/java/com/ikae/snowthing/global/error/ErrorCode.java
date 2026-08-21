package com.ikae.snowthing.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

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
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_001", "존재하지 않거나 이미 삭제된 댓글입니다."),
    PARENT_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_002", "존재하지 않는 부모 댓글입니다."),
    INVALID_COMMENT_PARENT(HttpStatus.BAD_REQUEST, "COMMENT_003", "동일한 게시글의 댓글에만 대댓글을 달 수 있습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 입력값입니다."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "COMMON_002", "페이지 크기는 1 이상 100 이하이어야 합니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
