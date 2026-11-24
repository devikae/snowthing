package com.ikae.snowthing.domain.admin.dto;

/**
 * 배너의 사진 주소와 이동 주소 형식. 공개 API로 그대로 나가 화면의 링크와 사진 주소가 되므로, 웹 주소(http, https)나 사이트 안 경로(/로 시작)만 받는다.
 * 스크립트를 실행하는 주소 형식이 저장되지 않게 한다. 길이는 DB 열 길이와 같다.
 */
public final class BannerUrlPolicy {

    public static final int MAX_LENGTH = 500;
    public static final String REGEXP = "^(?:https?://|/(?![/\\\\]))[^\\s\\\\]*$";
    public static final String MESSAGE = "주소는 http, https 또는 /로 시작해야 합니다.";

    private BannerUrlPolicy() {}
}
