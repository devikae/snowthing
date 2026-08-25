package com.ikae.snowthing.domain.member.repository;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.ikae.snowthing.domain.member.entity.QMember.member;
import static com.ikae.snowthing.domain.member.entity.QMemberResort.memberResort;
import static com.ikae.snowthing.domain.member.entity.QMemberRidingStyle.memberRidingStyle;
import static com.ikae.snowthing.domain.member.entity.QResort.resort;
import static com.ikae.snowthing.domain.member.entity.QRidingStyle.ridingStyle;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryCustomImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<MemberLoginResponse> findProfileByEmail(String email) {
        Member foundMember = queryFactory
                .selectFrom(member)
                .where(member.email.eq(email))
                .fetchOne();

        if (foundMember == null) {
            return Optional.empty();
        }

        List<String> resortNames = queryFactory
                .select(resort.name)
                .from(memberResort)
                .join(memberResort.resort, resort)
                .where(memberResort.member.id.eq(foundMember.getId()))
                .fetch();

        List<String> ridingStyleNames = queryFactory
                .select(ridingStyle.styleName)
                .from(memberRidingStyle)
                .join(memberRidingStyle.ridingStyle, ridingStyle)
                .where(memberRidingStyle.member.id.eq(foundMember.getId()))
                .fetch();

        return Optional.of(MemberLoginResponse.from(foundMember, resortNames, ridingStyleNames));
    }
}
