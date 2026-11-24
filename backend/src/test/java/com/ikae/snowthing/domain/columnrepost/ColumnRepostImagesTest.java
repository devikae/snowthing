package com.ikae.snowthing.domain.columnrepost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.domain.image.service.ImageService;
import com.ikae.snowthing.domain.image.service.PostImageLifecycleService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.BusinessException;

class ColumnRepostImagesTest {

    private static final String SOURCE =
            "https://gall.dcinside.com/mgallery/board/view/?id=snowboard&no=1";
    private static final String OWNER = "column-writer@example.com";
    private static final String DC = "https://dcimg1.dcinside.com/viewimage.php?id=a&no=b";
    private static final String MOVIE_PAGE =
            "https://gall.dcinside.com/board/movie/movie_view?no=7";
    private static final String MOVIE_FILE =
            "https://dcm1.dcinside.co.kr/viewmovie.php?code=a&key=b";
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
    private static final byte[] GIF = "GIF89a....".getBytes();
    private static final byte[] MP4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};

    private DcImageFetcher fetcher;
    private ImageService imageService;
    private PostImageLifecycleService lifecycle;
    private ColumnMediaStorage storage;
    private ColumnRepostImages images;
    private ImageService.PreparedPostImage prepared;

    @BeforeEach
    void setUp() {
        fetcher = mock(DcImageFetcher.class);
        imageService = mock(ImageService.class);
        lifecycle = mock(PostImageLifecycleService.class);
        storage = mock(ColumnMediaStorage.class);
        images = new ColumnRepostImages(fetcher, imageService, lifecycle, storage);
        prepared = new ImageService.PreparedPostImage("k", "image/jpeg", JPG, null, null);
    }

    private static DcImageFetcher.Downloaded file(byte[] bytes) throws IOException {
        return DcImageFetcher.save(new ByteArrayInputStream(bytes), 1024);
    }

    @Test
    @DisplayName("디시 사진은 원문 주소를 Referer로 받아 글쓴이 소유로 올리고 본문 주소를 바꾼다")
    void uploadsDcImages() throws IOException {
        given(fetcher.download(eq(DC), eq(SOURCE), anyLong())).willReturn(file(JPG));
        given(imageService.preparePostImage(any())).willReturn(prepared);
        given(lifecycle.upload(prepared, OWNER))
                .willReturn(new ImageUploadResponse("https://images.snowthing.org/n.jpg", "n"));

        String result = images.replace("앞\n\n![](" + DC + ")\n\n뒤", SOURCE, OWNER);

        assertThat(result).isEqualTo("앞\n\n![](https://images.snowthing.org/n.jpg)\n\n뒤");
    }

    @Test
    @DisplayName("같은 사진이 두 번 나오면 한 번만 올린다")
    void uploadsSameImageOnce() throws IOException {
        given(fetcher.download(eq(DC), eq(SOURCE), anyLong())).willReturn(file(JPG));
        given(imageService.preparePostImage(any())).willReturn(prepared);
        given(lifecycle.upload(prepared, OWNER))
                .willReturn(new ImageUploadResponse("https://images.snowthing.org/n.jpg", "n"));

        String result = images.replace("![](" + DC + ")\n![a](" + DC + ")", SOURCE, OWNER);

        assertThat(result)
                .isEqualTo(
                        "![](https://images.snowthing.org/n.jpg)\n![a](https://images.snowthing.org/n.jpg)");
        verify(fetcher, times(1)).download(eq(DC), eq(SOURCE), anyLong());
    }

    @Test
    @DisplayName("GIF는 크기를 줄이지 않고 그대로 올려 움직임을 남긴다")
    void gifIsStoredAsIs() throws IOException {
        given(fetcher.download(eq(DC), eq(SOURCE), anyLong())).willReturn(file(GIF));
        given(
                        storage.upload(
                                any(Path.class),
                                eq(ColumnMediaStorage.Use.GIF),
                                eq(DcImageFetcher.Kind.GIF)))
                .willReturn("https://images.snowthing.org/public/posts/clips/gif-1.gif");

        assertThat(images.replace("![](" + DC + ")", SOURCE, OWNER))
                .isEqualTo("![](https://images.snowthing.org/public/posts/clips/gif-1.gif)");
        verify(lifecycle, never()).upload(any(ImageService.PreparedPostImage.class), anyString());
    }

    @Test
    @DisplayName("GIF를 바꾼 mp4 클립은 반복 클립으로 올린다")
    void mp4ClipIsStoredAsClip() throws IOException {
        given(fetcher.download(eq(DC), eq(SOURCE), anyLong())).willReturn(file(MP4));
        given(
                        storage.upload(
                                any(Path.class),
                                eq(ColumnMediaStorage.Use.CLIP),
                                eq(DcImageFetcher.Kind.MP4)))
                .willReturn("https://images.snowthing.org/public/posts/clips/clip-1.mp4");

        assertThat(images.replace("![](" + DC + ")", SOURCE, OWNER))
                .isEqualTo("![](https://images.snowthing.org/public/posts/clips/clip-1.mp4)");
    }

    @Test
    @DisplayName("동영상 플레이어는 페이지에서 mp4를 찾아 재생 버튼 영상으로 올린다")
    void moviePageIsStoredAsMovie() throws IOException {
        given(fetcher.movieFileUrl(MOVIE_PAGE, SOURCE)).willReturn(Optional.of(MOVIE_FILE));
        given(fetcher.download(eq(MOVIE_FILE), eq(MOVIE_PAGE), anyLong())).willReturn(file(MP4));
        given(
                        storage.upload(
                                any(Path.class),
                                eq(ColumnMediaStorage.Use.MOVIE),
                                eq(DcImageFetcher.Kind.MP4)))
                .willReturn("https://images.snowthing.org/public/posts/clips/movie-1.mp4");

        assertThat(images.replace("![](" + MOVIE_PAGE + ")", SOURCE, OWNER))
                .isEqualTo("![](https://images.snowthing.org/public/posts/clips/movie-1.mp4)");
    }

    @Test
    @DisplayName("지워진 동영상처럼 페이지에 영상이 없으면 영상 원문 링크로 바꾼다")
    void missingMovieBecomesVideoLink() throws IOException {
        given(fetcher.movieFileUrl(MOVIE_PAGE, SOURCE)).willReturn(Optional.empty());

        assertThat(images.replace("![](" + MOVIE_PAGE + ")", SOURCE, OWNER))
                .isEqualTo("[▶ 영상은 원문에서 보기](" + SOURCE + ")");
    }

    @Test
    @DisplayName("상한을 넘거나 형식을 모르는 파일은 사진 원문 링크로 바꾼다")
    void tooLargeOrUnknownBecomesSourceLink() throws IOException {
        given(fetcher.download(eq(DC), eq(SOURCE), anyLong()))
                .willReturn(DcImageFetcher.save(new ByteArrayInputStream(new byte[20]), 10));
        assertThat(images.replace("![](" + DC + ")", SOURCE, OWNER))
                .isEqualTo("[사진은 원문에서 보기](" + SOURCE + ")");

        given(fetcher.download(eq(DC), eq(SOURCE), anyLong()))
                .willReturn(file("<html>".getBytes()));
        assertThat(images.replace("![](" + DC + ")", SOURCE, OWNER))
                .isEqualTo("[사진은 원문에서 보기](" + SOURCE + ")");
        verify(storage, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("크기나 해상도 검사에 걸린 사진도 원문 링크로 바꾼다")
    void rejectedImageBecomesSourceLink() throws IOException {
        given(fetcher.download(eq(DC), eq(SOURCE), anyLong())).willReturn(file(JPG));
        given(imageService.preparePostImage(any()))
                .willThrow(new BusinessException(ErrorCode.IMAGE_DIMENSIONS_EXCEEDED));

        assertThat(images.replace("![](" + DC + ")", SOURCE, OWNER))
                .isEqualTo("[사진은 원문에서 보기](" + SOURCE + ")");
    }

    @Test
    @DisplayName("디시가 아닌 사진 주소는 그대로 둔다")
    void keepsOtherImages() throws IOException {
        String content = "![](https://images.snowthing.org/a.jpg) https://youtu.be/g7JDKJCsrZA";

        assertThat(images.replace(content, SOURCE, OWNER)).isEqualTo(content);
        verify(fetcher, never()).download(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("미디어를 받지 못하면 예외를 던져 다음 게시 시각에 다시 시도하게 한다")
    void networkFailurePropagates() throws IOException {
        given(fetcher.download(eq(DC), eq(SOURCE), anyLong()))
                .willThrow(new IOException("timeout"));

        assertThatThrownBy(() -> images.replace("![](" + DC + ")", SOURCE, OWNER))
                .isInstanceOf(IOException.class);
    }
}
