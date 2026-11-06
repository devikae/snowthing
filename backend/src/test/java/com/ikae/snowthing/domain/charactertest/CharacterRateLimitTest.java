package com.ikae.snowthing.domain.charactertest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.security.SharedRequestLimiter;

class CharacterRateLimitTest {

    @Test
    @SuppressWarnings("unchecked")
    void 같은_와이파이의_친구들을_위해_제한은_넉넉하고_키_길이는_컬럼에_맞다() {
        SharedRequestLimiter shared = mock(SharedRequestLimiter.class);
        when(shared.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(true, 0));
        ContentWriteRateLimiter limiter = new ContentWriteRateLimiter(shared, true);
        ArgumentCaptor<List<SharedRequestLimiter.Limit>> captor =
                ArgumentCaptor.forClass(List.class);

        limiter.check(null, "1.2.3.4", ContentWriteRateLimiter.Action.CHARACTER_GROUP_JOIN);

        verify(shared).acquire(captor.capture());
        List<SharedRequestLimiter.Limit> limits = captor.getValue();
        assertThat(limits).extracting(SharedRequestLimiter.Limit::count).containsExactly(1, 300);
        assertThat(limits.get(0).duration().toSeconds()).isEqualTo(1);
        assertThat(limits)
                .allSatisfy(limit -> assertThat(limit.key()).hasSizeLessThanOrEqualTo(100));
    }
}
