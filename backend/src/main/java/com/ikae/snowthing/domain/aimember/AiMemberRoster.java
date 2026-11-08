package com.ikae.snowthing.domain.aimember;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.aimember.persona.Persona;

/** 기동 시 계정과 연결된 AI 멤버 목록. 연결 전에는 비어 있어 스케줄러가 아무것도 하지 않는다. */
@Component
@ConditionalOnProperty(name = AiMemberConfig.ENABLED_PROPERTY, havingValue = "true")
public class AiMemberRoster {

    public record AiMember(Persona persona, long memberId, String publicId) {}

    private volatile List<AiMember> members = List.of();

    void replace(List<AiMember> loaded) {
        members = List.copyOf(loaded);
    }

    public List<AiMember> members() {
        return members;
    }

    public Set<Long> memberIds() {
        return members.stream().map(AiMember::memberId).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> publicIds() {
        return members.stream().map(AiMember::publicId).collect(Collectors.toUnmodifiableSet());
    }

    public Optional<AiMember> byMemberId(long memberId) {
        return members.stream().filter(member -> member.memberId() == memberId).findFirst();
    }
}
