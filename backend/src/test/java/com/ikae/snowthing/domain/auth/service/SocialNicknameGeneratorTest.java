package com.ikae.snowthing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.member.validation.NicknamePolicy;

class SocialNicknameGeneratorTest {

    private final SocialNicknameGenerator generator = new SocialNicknameGenerator();

    @Test
    @DisplayName("유효한 2~10자 한글/영문/숫자 닉네임은 그대로 첫 후보로 사용한다")
    void initialCandidate_ValidNickname_ReturnsSanitized() {
        String candidate = generator.initialCandidate("설원보더123");

        assertThat(candidate).isEqualTo("설원보더123");
        assertThat(candidate).matches(NicknamePolicy.REGEXP);
    }

    @Test
    @DisplayName("특수문자나 공백이 섞인 소셜 닉네임은 허용 문자만 남기며 정책을 만족한다")
    void initialCandidate_SpecialChars_SanitizesAndMatchesPolicy() {
        String candidate = generator.initialCandidate("❄️ 스노우_보더!@# ");

        assertThat(candidate).isEqualTo("스노우보더");
        assertThat(candidate).matches(NicknamePolicy.REGEXP);
    }

    @Test
    @DisplayName("닉네임이 비어있거나 1자 이하이거나 10자를 초과하면 접두사 + 4자리 난수 닉네임을 생성한다")
    void initialCandidate_InvalidLength_GeneratesFallbackMatchingPolicy() {
        String fromBlank = generator.initialCandidate("   ");
        String fromSingleChar = generator.initialCandidate("김");
        String fromTooLong = generator.initialCandidate("열글자를초과하는아주긴소셜닉네임입니다");

        assertThat(fromBlank).startsWith("보더").hasSize(6).matches(NicknamePolicy.REGEXP);
        assertThat(fromSingleChar).startsWith("보더").hasSize(6).matches(NicknamePolicy.REGEXP);
        assertThat(fromTooLong).startsWith("열글자를초과").hasSize(10).matches(NicknamePolicy.REGEXP);
    }
}
