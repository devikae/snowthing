package com.ikae.snowthing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;

class AuthServiceTest {

    @Test
    void profileLookupUsesTheSameNormalizedEmailAsAuthentication() {
        MemberRepository repository = mock(MemberRepository.class);
        MemberLoginResponse expected = mock(MemberLoginResponse.class);
        given(repository.findProfileByEmail("member@snowthing.org"))
                .willReturn(Optional.of(expected));
        AuthService service = new AuthService(repository, new EmailNormalizer());

        assertThat(service.getMemberProfileByEmail(" Member@SnowThing.org ")).isSameAs(expected);
    }
}
