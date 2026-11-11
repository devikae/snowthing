package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 회원 상세 (IKA-78). 비밀번호와 토큰은 넣지 않고 비밀번호가 있는지만 준다. 이메일은 앞 두 글자만 보이게 가린다.
 *
 * <p>history는 admin_action_log의 이 회원 대상 조치다. 050 마이그레이션 전의 정지 이력은 남아 있지 않다.
 */
public record AdminMemberDetailResponse(
        String publicId,
        String maskedEmail,
        String nickname,
        String status,
        String role,
        LocalDateTime createdAt,
        boolean emailVerified,
        boolean hasPassword,
        List<String> socialProviders,
        long postCount,
        long commentCount,
        List<RecentPost> recentPosts,
        List<RecentComment> recentComments,
        List<AdminActionLogItem> history) {

    public record RecentPost(
            String publicId,
            String title,
            String categoryCode,
            String categoryName,
            String status,
            boolean isDeleted,
            LocalDateTime createdAt) {}

    public record RecentComment(
            Long commentId,
            String contentPreview,
            String postPublicId,
            String postTitle,
            String postCategoryCode,
            boolean isDeleted,
            boolean isHeld,
            LocalDateTime createdAt) {}

    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }
}
