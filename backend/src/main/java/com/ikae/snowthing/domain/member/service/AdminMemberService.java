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
