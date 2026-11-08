package com.ikae.snowthing.domain.aimember;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.aimember.AiMemberRoster.AiMember;
import com.ikae.snowthing.domain.aimember.persona.Persona;
import com.ikae.snowthing.domain.aimember.persona.PersonaFileLoader;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.config.AiMemberProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 페르소나 파일의 계정을 연결한다 (IKA-14). 마이그레이션 대신 기동 시 만들어 닉네임이 저장소와 마이그레이션 이력에 남지 않게 한다.
 *
 * <ul>
 *   <li>이메일로 찾고 없으면 비밀번호 없는(로그인 불가) 일반 회원으로 만든다.
 *   <li>같은 닉네임을 다른 회원이 쓰고 있으면 그 캐릭터는 건너뛴다.
 *   <li>이미 있는 계정의 닉네임은 바꾸지 않는다. 활성 상태가 아니면 연결하지 않는다.
 *   <li>로그에는 키와 회원 ID만 남긴다. 이메일·닉네임은 남기지 않는다.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = AiMemberConfig.ENABLED_PROPERTY, havingValue = "true")
public class AiMemberBootstrap {

    private final AiMemberProperties properties;
    private final MemberRepository memberRepository;
    private final AiMemberRoster roster;
    private final AiMemberQueryRepository queryRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        if (properties.personaFile().isBlank()) {
            log.warn("AI members enabled without a persona file; nothing to do");
            return;
        }
        roster.replace(link(PersonaFileLoader.load(Path.of(properties.personaFile()))));
    }

    @Transactional
    public List<AiMember> link(List<Persona> personas) {
        List<AiMember> linked = new ArrayList<>();
        for (Persona persona : personas) {
            Member member = memberRepository.findByEmail(persona.email()).orElse(null);
            if (member == null) {
                if (memberRepository.existsByNickname(persona.nickname())) {
                    log.warn("AI member skipped: key={}, reason=nickname taken", persona.key());
                    continue;
                }
                member =
                        memberRepository.save(
                                Member.builder()
                                        .email(persona.email())
                                        .nickname(persona.nickname())
                                        .role(Role.ROLE_USER)
                                        .status(MemberStatus.ACTIVE)
                                        .build());
                log.info("AI member created: key={}, memberId={}", persona.key(), member.getId());
            }
            if (member.getStatus() != MemberStatus.ACTIVE) {
                log.warn("AI member skipped: key={}, reason=not active", persona.key());
                continue;
            }
            if (queryRepository.ipPrefixUsedByOthers(persona.writerIp(), member.getId())) {
                log.warn("AI member ip prefix overlaps real writers: key={}", persona.key());
            }
            linked.add(new AiMember(persona, member.getId(), member.getPublicId()));
        }
        log.info("AI members linked: count={}", linked.size());
        return linked;
    }
}
