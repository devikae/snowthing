package com.ikae.snowthing.domain.charactertest.dto;

import com.ikae.snowthing.domain.charactertest.model.CharacterJob;
import com.ikae.snowthing.domain.charactertest.model.CharacterType;
import com.ikae.snowthing.domain.charactertest.model.PlanStyle;

/** API에 내보내는 캐릭터 표시. 유형 코드 대신 직업과 계획형, 즉흥형 구분만 담는다. */
public record CharacterLabel(CharacterJob job, PlanStyle planStyle) {
    public static CharacterLabel of(CharacterType type) {
        return new CharacterLabel(type.job(), type.planStyle());
    }
}
