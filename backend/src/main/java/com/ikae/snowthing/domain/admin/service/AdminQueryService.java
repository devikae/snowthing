package com.ikae.snowthing.domain.admin.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminActionLogItem;
import com.ikae.snowthing.domain.admin.dto.AdminCommentItem;
import com.ikae.snowthing.domain.admin.dto.AdminInquiryItem;
import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.admin.dto.AdminResortReportItem;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.admin.repository.AdminQueryRepository;
import com.ikae.snowthing.domain.resortreport.entity.ResortReportStatus;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 관리자 목록 조회 (IKA-78): 댓글, 1:1 문의, 설질 제보, 조치 기록. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueryService {

    private static final Set<String> COMMENT_FILTERS = Set.of("ALL", "VISIBLE", "HELD", "DELETED");
    private static final Set<String> INQUIRY_FILTERS = Set.of("ALL", "ANSWERED", "UNANSWERED");

    private final AdminQueryRepository repository;

    public AdminPage<AdminCommentItem> comments(
            CustomUserDetails admin,
            String filter,
            String query,
            String author,
            Integer page,
            Integer size) {
        AdminAccess.require(admin);
        String normalized = upper(filter, "ALL");
        if (!COMMENT_FILTERS.contains(normalized)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        AdminPaging paging = AdminPaging.of(page, size);
        return AdminPage.of(
                repository.findComments(
                        normalized,
                        AdminPaging.containsPattern(query),
                        AdminPaging.containsPattern(author),
                        paging.offset(),
                        paging.fetchLimit()),
                paging.page(),
                paging.size());
    }

    public AdminPage<AdminInquiryItem> inquiries(
            CustomUserDetails admin, String status, Integer page, Integer size) {
        AdminAccess.require(admin);
        String normalized = upper(status, "ALL");
        if (!INQUIRY_FILTERS.contains(normalized)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        AdminPaging paging = AdminPaging.of(page, size);
        return AdminPage.of(
                repository.findInquiries(normalized, paging.offset(), paging.fetchLimit()),
                paging.page(),
                paging.size());
    }

    public AdminPage<AdminResortReportItem> resortReports(
            CustomUserDetails admin, String status, Integer page, Integer size) {
        AdminAccess.require(admin);
        String normalized = upper(status, "ALL");
        String statusFilter =
                "ALL".equals(normalized) ? null : enumName(ResortReportStatus.class, normalized);
        AdminPaging paging = AdminPaging.of(page, size);
        return AdminPage.of(
                repository.findResortReports(statusFilter, paging.offset(), paging.fetchLimit()),
                paging.page(),
                paging.size());
    }

    public AdminPage<AdminActionLogItem> actionLogs(
            CustomUserDetails admin,
            String action,
            String targetType,
            String targetId,
            String actorPublicId,
            Integer page,
            Integer size) {
        AdminAccess.require(admin);
        AdminPaging paging = AdminPaging.of(page, size);
        String actionFilter =
                blank(action) ? null : enumName(AdminActionType.class, upper(action, ""));
        String targetTypeFilter =
                blank(targetType) ? null : enumName(AdminTargetType.class, upper(targetType, ""));
        String targetIdFilter = blank(targetId) ? null : targetId.strip();
        if (targetIdFilter != null && targetIdFilter.length() > 64) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Long actorId = null;
        if (!blank(actorPublicId)) {
            actorId = repository.findMemberIdByPublicId(actorPublicId.strip()).orElse(-1L);
        }
        return AdminPage.of(
                repository.findActionLogs(
                        actionFilter,
                        targetTypeFilter,
                        targetIdFilter,
                        actorId,
                        paging.offset(),
                        paging.fetchLimit()),
                paging.page(),
                paging.size());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String upper(String value, String fallback) {
        return blank(value) ? fallback : value.strip().toUpperCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> String enumName(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value).name();
        } catch (IllegalArgumentException invalid) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
