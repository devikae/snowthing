package com.ikae.snowthing.domain.member.repository;

import static com.ikae.snowthing.domain.member.entity.QMember.member;
import static com.ikae.snowthing.domain.member.entity.QMemberResort.memberResort;
import static com.ikae.snowthing.domain.member.entity.QMemberRidingStyle.memberRidingStyle;
import static com.ikae.snowthing.domain.member.entity.QResort.resort;
import static com.ikae.snowthing.domain.member.entity.QRidingStyle.ridingStyle;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryCustomImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 회원 프로필 조회 (1:N:M 다중 컬렉션 Cartesian Product 방지를 위한 3-Step O(1) 쿼리 분리 전략)
     *
     * <p>[아키텍처 설계 배경 및 트레이드오프]: 1. 문제점: Member 엔티티는 member_resorts(1:N)와
     * member_riding_styles(1:M)라는 2개의 독립된 일대다 관계를 가집니다. 이를 단일 SQL로 Fetch Join하면 N × M 카테시안
     * 곱(Cartesian Product)이 발생하여 중복 데이터 전송 및 메모리 오버헤드가 발생합니다. 2. 3-Step 고정 쿼리 전략: - Step 1: Member
     * 기본 정보 조회 (PK/Email 인덱스 단건 조회, 1 row) - Step 2: MemberId 기반 선호 Resort 이름 조회 (1~N rows) - Step
     * 3: MemberId 기반 선호 RidingStyle 이름 조회 (1~M rows) 3. 트레이드오프: - DB Network RTT가 1회 -> 3회로 발생하지만,
     * 회원 단건 조회이므로 데이터 크기에 비례하는 N+1이 아닌 고정 O(1) 쿼리로 동작합니다. - 카테시안 곱에 의한 메모리/네트워크 낭비를 물리적으로 방지하고 각
     * 쿼리가 인덱스 조건으로 매우 빠르게 수행됩니다.
     */
    @Override
    public Optional<MemberLoginResponse> findProfileByEmail(String email) {
        // Step 1: 회원 기본 엔티티 조회 (1 query)
        Member foundMember =
                queryFactory.selectFrom(member).where(member.email.eq(email)).fetchOne();

        if (foundMember == null) {
            return Optional.empty();
        }

        // Step 2: 선호 리조트명 조회 (1 query)
        List<String> resortNames =
                queryFactory
                        .select(resort.name)
                        .from(memberResort)
                        .join(memberResort.resort, resort)
                        .where(memberResort.member.id.eq(foundMember.getId()))
                        .fetch();

        // Step 3: 선호 라이딩 스타일명 조회 (1 query)
        List<String> ridingStyleNames =
                queryFactory
                        .select(ridingStyle.styleName)
                        .from(memberRidingStyle)
                        .join(memberRidingStyle.ridingStyle, ridingStyle)
                        .where(memberRidingStyle.member.id.eq(foundMember.getId()))
                        .fetch();

        return Optional.of(MemberLoginResponse.from(foundMember, resortNames, ridingStyleNames));
    }
}
