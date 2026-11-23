package com.ikae.snowthing.domain.columnrepost;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 휘구리 칼럼 예약 게시 설정(IKA-112). 기본은 꺼짐.
 *
 * @param memberEmail 글을 쓸 눈팅 계정 이메일
 * @param categoryCode 올릴 게시판 코드
 * @param maxAttempts 한 글이 이 횟수만큼 실패하면 그 글에서 멈춘다(순서를 지키려고 건너뛰지 않는다)
 * @param linkBaseUrl 디스코드 알림에 넣을 게시글 주소의 앞부분
 * @param imageTimeout 디시 사진 한 장을 받는 제한 시간
 */
@ConfigurationProperties(prefix = "snowthing.column-repost")
public record ColumnRepostProperties(
        boolean enabled,
        String memberEmail,
        String categoryCode,
        int maxAttempts,
        String linkBaseUrl,
        Duration imageTimeout) {

    public ColumnRepostProperties {
        memberEmail = memberEmail == null ? "" : memberEmail.trim();
        categoryCode =
                categoryCode == null || categoryCode.isBlank() ? "INFO" : categoryCode.trim();
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        linkBaseUrl =
                linkBaseUrl == null || linkBaseUrl.isBlank()
                        ? "http://localhost:3000"
                        : linkBaseUrl.trim().replaceAll("/+$", "");
        imageTimeout = imageTimeout == null ? Duration.ofSeconds(10) : imageTimeout;
    }
}
