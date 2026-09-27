package com.ikae.snowthing.domain.chat.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResortTagTest {

    @ParameterizedTest
    @CsvSource({
        "MUJU, MUJU",
        "무주, MUJU",
        "JISAN, JISAN",
        "지산, JISAN",
        "KONJIAM, KONJIAM",
        "곤지암, KONJIAM",
        "곤지, KONJIAM"
    })
    void from_acceptsNewResortCodesAndKoreanNames(String input, ResortTag expected) {
        assertThat(ResortTag.from(input)).isEqualTo(expected);
    }
}
