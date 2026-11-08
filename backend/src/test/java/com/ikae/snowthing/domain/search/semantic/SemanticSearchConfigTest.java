package com.ikae.snowthing.domain.search.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SemanticSearchConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SemanticSearchConfig.class);

    @Test
    @DisplayName("꺼진 상태에서 provider=http만 적어 두면 제공자 빈을 만들지 않고 기동한다")
    void disabledHttpProviderDoesNotBlockStartup() {
        runner.withPropertyValues("snowthing.search.semantic.provider=http")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(EmbeddingProvider.class);
                        });
    }

    @Test
    @DisplayName("켠 상태에서 provider=http 설정이 빠지면 기동을 막는다")
    void enabledHttpProviderWithoutSettingsFails() {
        runner.withPropertyValues(
                        "snowthing.search.semantic.enabled=true",
                        "snowthing.search.semantic.provider=http")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("켠 상태에서 provider=hashing이면 제공자 빈을 만든다")
    void enabledHashingProviderIsCreated() {
        runner.withPropertyValues(
                        "snowthing.search.semantic.enabled=true",
                        "snowthing.search.semantic.provider=hashing")
                .run(context -> assertThat(context).hasSingleBean(EmbeddingProvider.class));
    }
}
