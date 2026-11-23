package com.ikae.snowthing.domain.columnrepost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DcImageFetcherTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://dcimg1.dcinside.com/viewimage.php?id=3eb3&no=24b0",
                "https://dcimg8.dcinside.co.kr/viewimage.php?id=1",
                "https://image.dcinside.com/viewimage.php?id=1"
            })
    @DisplayName("디시 사진 호스트의 https 주소만 허용한다")
    void allowsDcImageHosts(String url) {
        assertThat(DcImageFetcher.isAllowed(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://dcimg1.dcinside.com/viewimage.php?id=1",
                "https://gall.dcinside.com/board/movie/movie_view?no=1",
                "https://dcimg1.dcinside.com.evil.example/x.jpg",
                "https://evil.example/dcimg1.dcinside.com/x.jpg",
                "https://user@dcimg1.dcinside.com/x.jpg",
                "https://dcimg1.dcinside.com:8443/x.jpg",
                "https://169.254.169.254/latest/meta-data",
                "not a url"
            })
    @DisplayName("다른 호스트, http, 포트 지정, 사용자 정보가 든 주소는 거절한다")
    void rejectsOtherUrls(String url) {
        assertThat(DcImageFetcher.isAllowed(url)).isFalse();
    }

    @Test
    @DisplayName("허용하지 않은 주소는 요청을 보내지 않고 실패한다")
    void fetchRejectsBeforeRequest() {
        DcImageFetcher fetcher = new DcImageFetcher(null, Duration.ofSeconds(1));

        assertThatThrownBy(() -> fetcher.fetch("https://evil.example/x.jpg", "https://gall"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("파일 앞부분으로 jpg, png, webp를 알아보고 gif는 지원하지 않는 형식으로 둔다")
    void detectsFormatBySignature() {
        assertThat(DcImageFetcher.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)).extension())
                .isEqualTo("jpg");
        assertThat(DcImageFetcher.detect(bytes(0x89, 'P', 'N', 'G', 0x0D)).extension())
                .isEqualTo("png");
        assertThat(
                        DcImageFetcher.detect(
                                        bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'))
                                .extension())
                .isEqualTo("webp");
        assertThat(DcImageFetcher.detect(bytes('G', 'I', 'F', '8', '9', 'a')).supported())
                .isFalse();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
