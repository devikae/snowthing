package com.ikae.snowthing.global.infra.s3;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    private static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public software.amazon.awssdk.services.cloudfront.CloudFrontClient cloudFrontClient() {
        return software.amazon.awssdk.services.cloudfront.CloudFrontClient.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(this::withTimeouts)
                .build();
    }

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    // SDK 2.30부터 PutObject에 CRC32 체크섬을 기본으로 붙인다. 업로드는 모두 서버가 AWS S3로 직접 보내므로
    // 기본값을 그대로 둔다. S3 호환 저장소(endpointOverride)로 바꿀 때는 WHEN_REQUIRED 설정을 검토한다.
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(this::withTimeouts)
                .build();
    }

    // SDK 기본값은 호출 시간 제한이 없다. 응답이 멈추면 요청 스레드나 예약 작업 스레드가 같이 멈추므로 상한을 둔다.
    // 같은 리전 S3에 사진(최대 6MB)을 올리는 시간보다 넉넉하게 잡는다.
    private void withTimeouts(ClientOverrideConfiguration.Builder builder) {
        builder.apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT).apiCallTimeout(API_CALL_TIMEOUT);
    }
}
