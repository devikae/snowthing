package com.ikae.snowthing.domain.post.service;

import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 1:1 문의 글의 접근 규칙. 작성자와 관리자만 본문, 댓글을 읽고 쓴다.
 *
 * <p>권한이 없으면 403이 아니라 {@code POST_NOT_FOUND}로 응답해 남의 문의가 있는지조차 드러내지 않는다.
 */
public final class InquiryAccess {

    private InquiryAccess() {}

    /** 문의 글이 아니면 그대로 통과한다. */
    public static void requireReadable(Post post, CustomUserDetails userDetails) {
        if (PostCategoryCodes.isInquiry(post) && !canRead(post, userDetails)) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
    }

    public static boolean canRead(Post post, CustomUserDetails userDetails) {
        if (userDetails == null) {
            return false;
        }
        return isAdmin(userDetails)
                || (post.getMember() != null
                        && post.getMember().getPublicId().equals(userDetails.getPublicId()));
    }

    public static boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails != null
                && userDetails.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.getKey()));
    }
}
