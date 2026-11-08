package com.ikae.snowthing.domain.charactertest.service;

import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.charactertest.dto.CharacterLabel;
import com.ikae.snowthing.domain.charactertest.dto.CharacterResultResponse;
import com.ikae.snowthing.domain.charactertest.model.CharacterAnswers;
import com.ikae.snowthing.domain.charactertest.model.CharacterCompatibilityTable;
import com.ikae.snowthing.domain.charactertest.model.CharacterType;

import lombok.RequiredArgsConstructor;

/** 답변으로 결과를 계산만 한다. 저장하지 않고 로그도 남기지 않는다. */
@Service
@RequiredArgsConstructor
public class CharacterResultService {

    private final CharacterCompatibilityTable compatibilityTable;

    public CharacterResultResponse compute(CharacterAnswers answers) {
        CharacterType type = answers.type();
        return new CharacterResultResponse(
                CharacterLabel.of(type),
                answers.stats(),
                answers.highlightQuestions(),
                compatibilityTable.bestMatchOf(type).map(CharacterLabel::of).orElse(null));
    }
}
