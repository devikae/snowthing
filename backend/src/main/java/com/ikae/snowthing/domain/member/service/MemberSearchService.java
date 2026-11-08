package com.ikae.snowthing.domain.member.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.dto.MemberSearchResponse;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 쪽지 받는 회원을 닉네임 앞글자로 찾는다. 본인과 비활성 회원은 어차피 보낼 수 없어 뺀다.
 *
 * <p>나를 차단한 회원은 빼지 않는다. 검색에서 사라지면 차단 사실이 드러나므로 보낼 때 일반 거절 메시지로 막는다. AI 회원도 구분되면 안 되므로 일반 회원과 똑같이
 * 찾는다.
 */
@Service
@RequiredArgsConstructor
public class MemberSearchService {

    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 50;
    static final int MAX_RESULTS = 10;

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<MemberSearchResponse> search(CustomUserDetails viewer, String nickname) {
        String keyword = nickname == null ? "" : nickname.trim();
        if (keyword.length() < MIN_LENGTH || keyword.length() > MAX_LENGTH) return List.of();

        return memberRepository
                .searchActiveByNicknamePrefix(
                        escapeLike(keyword) + "%",
                        viewer.getMember().getId(),
                        PageRequest.of(0, MAX_RESULTS))
                .stream()
                .map(MemberSearchResponse::from)
                .toList();
    }

    // 그대로 넘기면 "%%" 같은 입력으로 전체 회원이 조회된다.
    static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
