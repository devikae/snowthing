package com.ikae.snowthing.domain.search.service;

/**
 * 근거 원문을 여는 프런트 경로. 사이트 기준 상대 경로를 돌려준다.
 *
 * <p>카풀 글은 {@code /carpool/{publicId}}, 나머지 게시판은 {@code /posts/{publicId}} 상세 화면을 쓴다. 상세 화면에 댓글 앵커가
 * 없어 댓글 근거도 게시글 주소를 가리킨다.
 */
public final class EvidenceUrls {

    private static final String CARPOOL_CATEGORY_CODE = "CARPOOL";

    private EvidenceUrls() {}

    public static String postUrl(String categoryCode, String postPublicId) {
        if (CARPOOL_CATEGORY_CODE.equals(categoryCode)) {
            return "/carpool/" + postPublicId;
        }
        return "/posts/" + postPublicId;
    }
}
