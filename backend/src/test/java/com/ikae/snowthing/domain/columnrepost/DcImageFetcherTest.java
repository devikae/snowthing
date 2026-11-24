package com.ikae.snowthing.domain.columnrepost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
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
    @DisplayName("디시 사진 서버의 https 주소만 허용한다")
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
    @DisplayName("다른 호스트, http, 포트 지정, 사용자 정보가 든 주소는 사진 주소로 받지 않는다")
    void rejectsOtherUrls(String url) {
        assertThat(DcImageFetcher.isAllowed(url)).isFalse();
    }

    @Test
    @DisplayName("동영상 플레이어 페이지는 gall.dcinside.com의 movie_view?no=숫자만 받는다")
    void moviePageShape() {
        assertThat(
                        DcImageFetcher.isMoviePage(
                                "https://gall.dcinside.com/board/movie/movie_view?no=7462979"))
                .isTrue();
        assertThat(
                        DcImageFetcher.isMoviePage(
                                "https://gall.dcinside.com/board/movie/movie_view?no=1&x=2"))
                .isFalse();
        assertThat(
                        DcImageFetcher.isMoviePage(
                                "http://gall.dcinside.com/board/movie/movie_view?no=1"))
                .isFalse();
        assertThat(DcImageFetcher.isMoviePage("https://gall.dcinside.com/board/view/?no=1"))
                .isFalse();
        assertThat(DcImageFetcher.isMoviePage("https://evil.example/board/movie/movie_view?no=1"))
                .isFalse();
    }

    @Test
    @DisplayName("동영상 페이지에서 디시 동영상 서버의 mp4 주소만 꺼낸다")
    void movieSourceFromPage() {
        String page =
                "<video id=\"dc_mv\"><source src=\"https://dcm1.dcinside.co.kr/viewmovie.php?code=a&amp;key=b&amp;type=mp4\" type=\"video/mp4\"></video>";
        assertThat(DcImageFetcher.movieSource(page))
                .contains("https://dcm1.dcinside.co.kr/viewmovie.php?code=a&key=b&type=mp4");
        assertThat(DcImageFetcher.movieSource("<source src=\"https://evil.example/a.mp4\">"))
                .isEmpty();
        assertThat(DcImageFetcher.movieSource("<p>삭제된 동영상</p>")).isEmpty();
    }

    @Test
    @DisplayName("허용하지 않은 주소는 요청을 보내지 않고 실패한다")
    void downloadRejectsBeforeRequest() {
        DcImageFetcher fetcher = new DcImageFetcher(null, Duration.ofSeconds(1));

        assertThatThrownBy(() -> fetcher.download("https://evil.example/x.jpg", "https://gall", 10))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> fetcher.movieFileUrl("https://evil.example/movie_view?no=1", "r"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("받은 파일은 임시 파일에 두고, 상한을 넘으면 멈추고 지운다")
    void savesToTempFileWithinLimit() throws IOException {
        byte[] gif = "GIF89a-animation".getBytes();
        try (DcImageFetcher.Downloaded saved =
                DcImageFetcher.save(new ByteArrayInputStream(gif), 100)) {
            assertThat(saved.kind()).isEqualTo(DcImageFetcher.Kind.GIF);
            assertThat(saved.size()).isEqualTo(gif.length);
            assertThat(Files.readAllBytes(saved.file())).isEqualTo(gif);
            saved.close();
            assertThat(saved.file()).doesNotExist();
        }

        DcImageFetcher.Downloaded tooLarge =
                DcImageFetcher.save(new ByteArrayInputStream(new byte[200]), 100);
        assertThat(tooLarge.tooLarge()).isTrue();
        assertThat(tooLarge.file()).isNull();
    }

    @Test
    @DisplayName("파일 앞부분으로 jpg, png, webp, gif, mp4를 알아본다")
    void detectsFormatBySignature() {
        assertThat(DcImageFetcher.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
                .isEqualTo(DcImageFetcher.Kind.JPG);
        assertThat(DcImageFetcher.detect(bytes(0x89, 'P', 'N', 'G', 0x0D)))
                .isEqualTo(DcImageFetcher.Kind.PNG);
        assertThat(DcImageFetcher.detect(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P')))
                .isEqualTo(DcImageFetcher.Kind.WEBP);
        assertThat(DcImageFetcher.detect(bytes('G', 'I', 'F', '8', '9', 'a')))
                .isEqualTo(DcImageFetcher.Kind.GIF);
        assertThat(
                        DcImageFetcher.detect(
                                bytes(0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm')))
                .isEqualTo(DcImageFetcher.Kind.MP4);
        assertThat(DcImageFetcher.detect(bytes('<', 'h', 't', 'm', 'l')))
                .isEqualTo(DcImageFetcher.Kind.UNKNOWN);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }
}
