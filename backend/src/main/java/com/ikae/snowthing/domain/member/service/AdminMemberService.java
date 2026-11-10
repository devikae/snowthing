package com.ikae.snowthing.domain.member.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.dto.AdminMemberListResponse;
import com.ikae.snowthing.domain.member.dto.AdminMemberResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.config.websocket.ChatConnectionRegistry;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.MemberSessionRegistry;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 회원 조회와 정지, 해제.
 *
 * <p>정지는 커밋 뒤에 세션과 라이브톡 연결을 끊는다. 먼저 끊으면 커밋 전에 들어온 로그인이 활성 상태를 읽어 새 세션을 만들 수 있다. 이미 정지된 회원을 다시 정지해도
 * 끊기는 다시 수행하고, 감사 로그는 상태가 실제로 바뀔 때만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("admin.audit");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 100;

    private final MemberRepository memberRepository;
    private final AdminMemberStatusWriter statusWriter;
    private final AdminMemberRoleWriter roleWriter;
    private final MemberSessionRegistry memberSessionRegistry;
    private final ChatConnectionRegistry chatConnectionRegistry;

    @Transactional(readOnly = true)
    public AdminMemberListResponse search(String query, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        String keyword = query == null ? "" : query.strip();
        if (pageNumber < 0
                || pageSize < 1
                || pageSize > MAX_SIZE
                || keyword.length() > MAX_QUERY_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Slice<Member> members =
                memberRepository.searchForAdmin(
                        "%" + MemberSearchService.escapeLike(keyword) + "%",
                        PageRequest.of(pageNumber, pageSize));
        return new AdminMemberListResponse(
                members.map(AdminMemberResponse::from).getContent(),
                pageNumber,
                pageSize,
                members.hasNext());
    }

    public AdminMemberResponse suspend(String actorPublicId, String targetPublicId) {
        AdminMemberStatusWriter.Change change = statusWriter.suspend(actorPublicId, targetPublicId);
        memberSessionRegistry.invalidateAll(targetPublicId);
        chatConnectionRegistry.disconnectMember(change.memberId());
        audit("SUSPEND", actorPublicId, change);
        return change.member();
    }

    public AdminMemberResponse reinstate(String actorPublicId, String targetPublicId) {
        AdminMemberStatusWriter.Change change = statusWriter.reinstate(targetPublicId);
        audit("REINSTATE", actorPublicId, change);
        return change.member();
    }

    /** 관리자로 지정한다. 세션 권한은 로그인 때 정해지므로 커밋 뒤 대상의 기존 세션을 끊어 다시 로그인하면 바로 관리자 화면을 쓰게 한다. */
    public AdminMemberResponse grantAdmin(String actorPublicId, String targetPublicId) {
        AdminMemberRoleWriter.Change change = roleWriter.grant(actorPublicId, targetPublicId);
        afterRoleChange("GRANT_ADMIN", actorPublicId, change);
        return change.member();
    }

    /** 관리자 권한을 해제한다. 커밋 뒤 대상의 기존 세션을 끊어 세션에 남은 ROLE_ADMIN을 바로 없앤다. */
    public AdminMemberResponse revokeAdmin(String actorPublicId, String targetPublicId) {
        AdminMemberRoleWriter.Change change = roleWriter.revoke(actorPublicId, targetPublicId);
        afterRoleChange("REVOKE_ADMIN", actorPublicId, change);
        return change.member();
    }

    private void afterRoleChange(
            String action, String actorPublicId, AdminMemberRoleWriter.Change change) {
        if (!change.changed()) {
            return;
        }
        memberSessionRegistry.invalidateAll(change.member().publicId());
        AUDIT_LOG.info(
                "action={} actor={} target={} from={} to={}",
                action,
                actorPublicId,
                change.member().publicId(),
                change.previous(),
                change.member().role());
    }

    private static void audit(
            String action, String actorPublicId, AdminMemberStatusWriter.Change change) {
        if (change.changed()) {
            AUDIT_LOG.info(
                    "action={} actor={} target={} from={} to={}",
                    action,
                    actorPublicId,
                    change.member().publicId(),
                    change.previous(),
                    change.member().status());
        }
    }
}
