package com.ikae.snowthing.domain.columnrepost;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * GIF와 영상을 고치지 않고 그대로 S3에 올린다. 눈팅 사진 업로드는 jpg, png, webp만 받아 크기를 줄이므로, 움직이는 미디어는 이 길로만 올린다.
 *
 * <p>키는 {@code public/posts/clips/{종류}-{UUID}.{확장자}}다. 사진 정리 작업은 {@code public/posts/originals}의
 * UUID 사진만 지우므로 이 파일은 글을 지워도 남는다. 화면은 파일 이름 앞의 종류(clip, movie)로 반복 클립과 재생 버튼 영상을 나눈다.
 */
@Component
public class ColumnMediaStorage {

    static final String PREFIX = "public/posts/clips/";

    private final S3Client s3;
    private final String bucket;
    private final String baseUrl;

    public ColumnMediaStorage(
            S3Client s3,
            @Value("${cloud.aws.s3.bucket:snowthing-media-00001}") String bucket,
            @Value("${cloud.aws.cloudfront.base-url:https://images.snowthing.org}")
                    String baseUrl) {
        this.s3 = s3;
        this.bucket = bucket;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** 미디어 종류. 파일 이름 앞에 붙어 화면이 그리는 방식을 정한다. */
    public enum Use {
        /** GIF 원본. 사진처럼 보인다. */
        GIF("gif"),
        /** 디시가 GIF를 바꿔 둔 짧은 mp4. 소리 없이 자동 반복한다. */
        CLIP("clip"),
        /** 디시 동영상 플레이어 영상. 재생 버튼으로 본다. */
        MOVIE("movie");

        private final String name;

        Use(String name) {
            this.name = name;
        }
    }

    /**
     * @return CDN 주소
     */
    public String upload(Path file, Use use, DcImageFetcher.Kind kind) {
        String key = PREFIX + use.name + "-" + UUID.randomUUID() + "." + kind.extension();
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(kind.contentType())
                        .cacheControl("public, max-age=31536000, immutable")
                        .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(60)))
                        .build(),
                RequestBody.fromFile(file));
        return baseUrl + "/" + key;
    }
}
