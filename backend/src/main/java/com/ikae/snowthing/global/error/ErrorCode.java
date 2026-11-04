package com.ikae.snowthing.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "이메일 또는 비밀번호가 일치하지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_002", "해당 작업을 수행할 권한이 없습니다."),
    OAUTH_PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST, "AUTH_003", "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH_PROVIDER_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE, "AUTH_004", "소셜 로그인 설정이 완료되지 않았습니다."),
    OAUTH_STATE_INVALID(
            HttpStatus.BAD_REQUEST, "AUTH_005", "소셜 로그인 인증 상태가 만료되었거나 유효하지 않습니다. 다시 시도해 주세요."),
    OAUTH_AUTHENTICATION_FAILED(
            HttpStatus.UNAUTHORIZED, "AUTH_006", "소셜 로그인 인증에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    OAUTH_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_007", "소셜 계정의 이메일 제공 동의가 필요합니다."),
    OAUTH_EMAIL_NOT_VERIFIED(
            HttpStatus.BAD_REQUEST, "AUTH_008", "인증되지 않은 소셜 이메일 계정으로는 로그인할 수 없습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "AUTH_009", "이용이 정지된 계정입니다."),
    ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "AUTH_010", "탈퇴 처리된 계정입니다."),
    OAUTH_RATE_LIMIT(
            HttpStatus.TOO_MANY_REQUESTS, "AUTH_011", "소셜 로그인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    OAUTH_ACCOUNT_LINK_REQUIRED(
            HttpStatus.CONFLICT, "AUTH_012", "이미 가입된 이메일입니다. 기존 로그인 방법을 이용해 주세요."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "존재하지 않는 회원입니다."),
    PROFILE_LIMIT_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, "MEMBER_007", "요청을 일시적으로 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    PROFILE_CDN_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE, "FILE_006", "프로필 이미지 캐시 정리 설정이 필요합니다."),
    REGISTRATION_POLICY_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, "MEMBER_008", "가입 동의 문서를 불러올 수 없습니다."),
    REGISTRATION_POLICY_CHANGED(
            HttpStatus.CONFLICT, "MEMBER_009", "가입 동의 문서가 변경되었습니다. 새로고침 후 다시 확인해 주세요."),
    CHAT_BOT_BUDGET_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "CHAT_010", "봇의 전체 호출 한도에 도달했습니다."),
    CHAT_BOT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_011", "봇 호출 한도를 확인할 수 없습니다."),
    CHAT_BOT_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_012", "봇의 대기 요청이 많습니다."),
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "MEMBER_002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.BAD_REQUEST, "MEMBER_003", "이미 사용 중인 닉네임입니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "MEMBER_004", "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_CHANGE_UNAVAILABLE(
            HttpStatus.BAD_REQUEST, "MEMBER_005", "소셜 로그인 계정의 비밀번호는 로그인 제공자에서 변경해 주세요."),
    PROFILE_ACTION_LIMIT(
            HttpStatus.TOO_MANY_REQUESTS, "MEMBER_006", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    IMAGE_GLOBAL_LIMIT(
            HttpStatus.TOO_MANY_REQUESTS,
            "FILE_007",
            "전체 업로드 한도에 도달하여 이미지 업로드가 일시적으로 제한됩니다. 잠시 후 다시 시도해 주세요."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "EMAIL_001", "이미 가입된 이메일입니다."),
    EMAIL_CODE_FORMAT_INVALID(HttpStatus.BAD_REQUEST, "EMAIL_002", "인증번호는 숫자 6자리여야 합니다."),
    EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "EMAIL_003", "인증번호가 일치하지 않습니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.GONE, "EMAIL_004", "인증번호가 만료되었습니다. 다시 요청해 주세요."),
    EMAIL_ATTEMPT_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_005", "인증번호 입력 횟수를 초과했습니다."),
    EMAIL_SEND_LIMIT(
            HttpStatus.TOO_MANY_REQUESTS, "EMAIL_006", "인증 메일 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    EMAIL_DELIVERY_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_007", "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요."),
    EMAIL_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "EMAIL_008", "이메일 인증 정보가 유효하지 않습니다."),
    EMAIL_TOKEN_EXPIRED(HttpStatus.GONE, "EMAIL_009", "이메일 인증이 만료되었거나 이미 사용되었습니다."),
    EMAIL_AVAILABILITY_LIMIT(
            HttpStatus.TOO_MANY_REQUESTS, "EMAIL_010", "이메일 중복 확인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_001", "존재하지 않거나 삭제된 게시글입니다."),
    POST_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_002", "존재하지 않는 게시판 카테고리입니다."),
    ALREADY_REACTED(HttpStatus.CONFLICT, "POST_003", "이미 추천 또는 비추천 투표를 완료한 게시글입니다."),
    INVALID_ANON_PASSWORD(HttpStatus.FORBIDDEN, "POST_004", "비회원 익명 비밀번호가 일치하지 않습니다."),
    INVALID_PAGE_LIMIT(
            HttpStatus.BAD_REQUEST,
            "POST_005",
            "게시판 조회의 최대 한계선은 100페이지(2,000개 글)까지입니다. 더 이전 글은 검색 기능을 이용해 주세요."),
    ANONYMOUS_POST_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "POST_006", "해당 게시판은 익명 작성을 지원하지 않습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_001", "존재하지 않거나 이미 삭제된 댓글입니다."),
    PARENT_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_002", "존재하지 않는 부모 댓글입니다."),
    INVALID_COMMENT_PARENT(HttpStatus.BAD_REQUEST, "COMMENT_003", "동일한 게시글의 댓글에만 대댓글을 달 수 있습니다."),
    COMMENT_REPLY_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "COMMENT_004", "루트 댓글 1개당 작성 가능한 대댓글 수는 최대 100개입니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 입력값입니다."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "COMMON_002", "페이지 크기는 1 이상 100 이하이어야 합니다."),
    MARKET_LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "MARKET_001", "존재하지 않거나 조회할 수 없는 중고장터 판매글입니다."),
    MARKET_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "MARKET_002", "존재하지 않는 중고장터 상품 분류입니다."),
    MARKET_CATEGORY_INACTIVE(HttpStatus.BAD_REQUEST, "MARKET_003", "현재 선택할 수 없는 중고장터 상품 분류입니다."),
    MARKET_INVALID_PRICE(HttpStatus.BAD_REQUEST, "MARKET_004", "중고장터 가격과 무료 나눔 설정을 확인해 주세요."),
    MARKET_IMAGE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "MARKET_005", "중고장터 이미지는 최대 10장까지 등록할 수 있습니다."),
    MARKET_LISTING_UPDATE_CONFLICT(
            HttpStatus.CONFLICT, "MARKET_006", "다른 화면에서 판매글이 먼저 수정됐습니다. 최신 내용을 다시 확인해 주세요."),
    MARKET_REACTION_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST, "MARKET_007", "중고장터 판매글은 추천과 비추천을 지원하지 않습니다."),
    MARKET_ANONYMOUS_COMMENT_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST, "MARKET_008", "중고장터 댓글은 익명으로 작성할 수 없습니다."),
    MARKET_INVALID_TRADE_STATUS(HttpStatus.BAD_REQUEST, "MARKET_009", "지원하지 않는 중고장터 거래 상태입니다."),
    MARKET_INVALID_PAGE_SIZE(
            HttpStatus.BAD_REQUEST, "MARKET_010", "중고장터 페이지 크기는 1 이상 50 이하이어야 합니다."),
    MARKET_PAGE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "MARKET_011", "중고장터는 최대 100페이지까지 조회할 수 있습니다."),
    INVALID_CARPOOL_SCHEDULE(HttpStatus.BAD_REQUEST, "CARPOOL_001", "카풀 출발·복귀 일정을 확인해 주세요."),
    INVALID_CARPOOL_VALUE(HttpStatus.BAD_REQUEST, "CARPOOL_002", "카풀 인원·연비·거리·통행료 값을 확인해 주세요."),
    CARPOOL_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "CARPOOL_003", "카풀 상세 정보가 필요합니다."),
    ROUTE_CALCULATION_FAILED(
            HttpStatus.BAD_REQUEST, "CARPOOL_004", "경로를 자동 계산하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    FUEL_PRICE_PROVIDER_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "CARPOOL_005",
            "현재 유가 정보를 조회할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    ROUTE_PROVIDER_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE, "CARPOOL_006", "카카오 길찾기 API 설정이 완료되지 않았습니다."),
    FUEL_PRICE_PROVIDER_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE, "CARPOOL_007", "오피넷 유가 API 설정이 완료되지 않았습니다."),
    CARPOOL_EXTERNAL_API_RATE_LIMIT(
            HttpStatus.TOO_MANY_REQUESTS, "CARPOOL_008", "경로·장소 조회 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    FUEL_PRICE_INPUT_REQUIRED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "CARPOOL_009",
            "현재 유가를 조회할 수 없습니다. 리터당 유가를 직접 입력해 주세요."),
    CARPOOL_PAGE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "CARPOOL_010", "카풀 목록은 최대 100페이지까지 조회할 수 있습니다."),
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
    INVALID_FILE_TYPE(
            HttpStatus.BAD_REQUEST, "FILE_001", "지원하지 않는 파일 형식입니다. JPG, PNG, WEBP 이미지만 업로드 가능합니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FILE_002", "파일 크기는 최대 5MB까지 업로드 가능합니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE_003", "존재하지 않는 파일입니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_004", "파일 업로드에 실패했습니다."),
    IMAGE_DIMENSIONS_EXCEEDED(
            HttpStatus.BAD_REQUEST, "FILE_005", "이미지는 한 변 10,000px, 총 2,500만 화소 이하로 줄여 주세요."),
    RESORT_NOT_FOUND(HttpStatus.NOT_FOUND, "RESORT_001", "존재하지 않거나 비활성화된 리조트입니다."),
    INVALID_RESORT_REPORT_CONTENT(
            HttpStatus.BAD_REQUEST, "REPORT_001", "설질 제보 내용은 1자 이상 100자 이하여야 합니다."),
    RESORT_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT_002", "존재하지 않거나 확인할 수 없는 설질 제보입니다."),
    RESORT_REPORT_DAILY_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS, "REPORT_003", "설질 제보는 하루에 최대 5개까지 작성할 수 있습니다."),
    RESORT_REPORT_SEASON_CLOSED(
            HttpStatus.FORBIDDEN, "REPORT_004", "설질 제보는 10월부터 다음 해 4월까지만 작성할 수 있습니다."),
    RESORT_REPORT_PAGE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "REPORT_005", "설질 제보는 최대 100페이지까지 조회할 수 있습니다."),
    RESORT_REPORT_INVALID_PAGE_SIZE(
            HttpStatus.BAD_REQUEST, "REPORT_006", "설질 제보 페이지 크기는 1 이상 100 이하이어야 합니다."),
    SEARCH_INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "SEARCH_001",
            "검색어는 2자 이상 100자 이하, 결과 수는 1 이상 20 이하, 근거 ID와 해시는 정해진 형식이어야 합니다."),
    EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "EVIDENCE_001", "존재하지 않거나 볼 수 없는 근거입니다."),
    EVIDENCE_HASH_MISMATCH(HttpStatus.CONFLICT, "EVIDENCE_002", "근거 원문이 검색 이후 바뀌었습니다. 다시 검색해 주세요."),
    API_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "API_TOKEN_001", "유효하지 않거나 만료되었거나 폐기된 API 토큰입니다."),
    API_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "API_TOKEN_002", "존재하지 않는 API 토큰입니다."),
    API_TOKEN_INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "API_TOKEN_003",
            "토큰 이름은 1자 이상 50자 이하, 범위는 READ 또는 DRAFT, 만료 기간은 1일 이상 30일 이하여야 합니다."),
    API_TOKEN_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT, "API_TOKEN_004", "사용 중인 API 토큰은 최대 10개까지 만들 수 있습니다."),
    ANSWER_DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "DRAFT_001", "존재하지 않는 답변 초안입니다."),
    ANSWER_DRAFT_INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "DRAFT_002",
            "대상 게시글 ID, 본문(1자 이상 1,000자 이하), 근거(1개 이상 20개 이하, 중복 없음, 해시 필수), 멱등성 키 형식을 확인해 주세요."),
    ANSWER_DRAFT_TARGET_NOT_FOUND(
            HttpStatus.NOT_FOUND, "DRAFT_003", "답변 대상 게시글이 없거나 볼 수 없습니다."),
    ANSWER_DRAFT_IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT, "DRAFT_004", "같은 멱등성 키로 다른 내용의 초안이 이미 저장되어 있습니다."),
    ANSWER_DRAFT_INVALID_STATUS(HttpStatus.CONFLICT, "DRAFT_005", "현재 초안 상태에서는 할 수 없는 작업입니다."),
    ANSWER_DRAFT_PUBLISH_DISABLED(HttpStatus.FORBIDDEN, "DRAFT_006", "답변 게시 기능이 꺼져 있습니다."),
    ANSWER_DRAFT_TARGET_UNAVAILABLE(
            HttpStatus.CONFLICT, "DRAFT_007", "대상 게시글이 삭제되었거나 볼 수 없게 되어 게시할 수 없습니다."),
    ANSWER_DRAFT_TARGET_CHANGED(
            HttpStatus.CONFLICT, "DRAFT_008", "승인 이후 대상 게시글이 수정되었습니다. 다시 검토해 주세요."),
    ANSWER_DRAFT_EVIDENCE_CHANGED(
            HttpStatus.CONFLICT, "DRAFT_009", "초안 저장 이후 근거 원문이 바뀌었거나 볼 수 없게 되었습니다."),
    ANSWER_DRAFT_APPROVER_INVALID(
            HttpStatus.CONFLICT, "DRAFT_010", "승인한 회원이 더 이상 활성 관리자가 아니어서 게시할 수 없습니다."),
    ANSWER_DRAFT_UPDATE_CONFLICT(
            HttpStatus.CONFLICT, "DRAFT_011", "다른 요청이 초안을 먼저 바꿨습니다. 새로고침 후 다시 시도해 주세요."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
