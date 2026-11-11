package com.ikae.snowthing.domain.post.entity;

import java.util.List;

/**
 * 공개 범위가 다른 게시판 카테고리 코드. 공개 목록과 근거 검색에서 빠지는 카테고리는 {@link #HIDDEN_FROM_PUBLIC} 한 곳에서 정한다.
 *
 * <p>중고장터는 판매글 전용 화면에서만 보이고, 1:1 문의는 작성자와 관리자만 본다. 근거 검색의 SQL 조건({@code SearchVisibilitySql})도 같은
 * 목록을 쓴다.
 *
 * <p>카풀은 검색에는 나오지만 게시판 목록에는 나오지 않는다({@link #HIDDEN_FROM_BOARD_LIST}). 상세, 수정, 삭제는 카풀 전용 화면과 API만
 * 쓴다.
 */
public final class PostCategoryCodes {

    public static final String MARKET = "MARKET";
    public static final String NOTICE = "NOTICE";
    public static final String INQUIRY = "INQUIRY";
    public static final String CARPOOL = "CARPOOL";
    public static final String ANONYMOUS = "ANONYMOUS";

    public static final List<String> HIDDEN_FROM_PUBLIC = List.of(MARKET, INQUIRY);
    public static final List<String> HIDDEN_FROM_BOARD_LIST = List.of(MARKET, INQUIRY, CARPOOL);

    private PostCategoryCodes() {}

    public static boolean hasOwnScreen(String categoryCode) {
        return MARKET.equalsIgnoreCase(categoryCode) || CARPOOL.equalsIgnoreCase(categoryCode);
    }

    public static boolean isInquiry(String categoryCode) {
        return INQUIRY.equalsIgnoreCase(categoryCode);
    }

    public static boolean isInquiry(Post post) {
        return isInquiry(post.getCategory().getCode());
    }

    /** 익명게시판 글. 비회원 추천과 비추천은 이 게시판에서만 받는다. */
    public static boolean isAnonymousBoard(Post post) {
        return ANONYMOUS.equalsIgnoreCase(post.getCategory().getCode());
    }
}
