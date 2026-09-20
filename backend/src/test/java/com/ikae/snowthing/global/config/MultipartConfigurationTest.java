package com.ikae.snowthing.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class MultipartConfigurationTest {

    @Test
    @DisplayName("Spring multipart 제한은 서비스의 5MB 제한과 요청 오버헤드를 반영한다")
    void multipartLimits_matchServicePolicy() throws IOException {
        List<PropertySource<?>> propertySources =
                new YamlPropertySourceLoader()
                        .load(
                                "application",
                                new FileSystemResource("src/main/resources/application.yml"));

        PropertySource<?> application = propertySources.get(0);
        assertThat(application.getProperty("spring.servlet.multipart.max-file-size"))
                .isEqualTo("5MB");
        assertThat(application.getProperty("spring.servlet.multipart.max-request-size"))
                .isEqualTo("6MB");
    }
}
