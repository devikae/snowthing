package com.ikae.snowthing.domain.aimember;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ikae.snowthing.domain.aimember.compose.HttpAiComposeGateway;
import com.ikae.snowthing.global.config.AiMemberProperties;

class AiMemberDisabledTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            AiMemberConfig.class,
                            AiMemberRoster.class,
                            AiMemberBootstrap.class,
                            AiActivityScheduler.class,
                            AiMemberActionService.class,
                            HttpAiComposeGateway.class);

    @Test
    @DisplayName("기본값(꺼짐)이면 AI 멤버 빈이 하나도 생기지 않아 봇 서버를 부를 수 없다")
    void createsNoBeansByDefault() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(AiMemberProperties.class);
                    assertThat(context).doesNotHaveBean(ScheduledExecutorService.class);
                    assertThat(context).doesNotHaveBean(AiActivityScheduler.class);
                    assertThat(context).doesNotHaveBean(AiMemberActionService.class);
                    assertThat(context).doesNotHaveBean(HttpAiComposeGateway.class);
                    assertThat(context).doesNotHaveBean(AiMemberBootstrap.class);
                    assertThat(context.getBean(AiMemberProperties.class).enabled()).isFalse();
                });
    }
}
