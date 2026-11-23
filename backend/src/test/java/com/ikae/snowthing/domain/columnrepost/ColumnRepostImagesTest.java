package com.ikae.snowthing.domain.columnrepost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;

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
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};

    private DcImageFetcher fetcher;
    private ImageService imageService;
    private PostImageLifecycleService lifecycle;
    private ColumnRepostImages images;
    private ImageService.PreparedPostImage prepared;

    @BeforeEach
    void setUp() {
        fetcher = mock(DcImageFetcher.class);
        imageService = mock(ImageService.class);
        lifecycle = mock(PostImageLifecycleService.class);
        images = new ColumnRepostImages(fetcher, imageService, lifecycle);
        prepared = new ImageService.PreparedPostImage("k", "image/jpeg", JPG, null, null);
    }

    @Test
    @DisplayName("디시 사진은 원문 주소를 Referer로 받아 글쓴이 소유로 올리고 본문 주소를 바꾼다")
    void uploadsDcImages() throws IOException {
        given(fetcher.fetch(DC, SOURCE)).willReturn(DcImageFetcher.detect(JPG));
        given(imageService.preparePostImage(any())).willReturn(prepared);
        given(lifecycle.upload(prepared, OWNER))
                .willReturn(new ImageUploadResponse("https://images.snowthing.org/n.jpg", "n"));

        String result = images.replace("앞\n\n![](" + DC + ")\n\n뒤", SOURCE, OWNER);

        assertThat(result).isEqualTo("앞\n\n![](https://images.snowthing.org/n.jpg)\n\n뒤");
    }

    @Test
    @DisplayName("같은 사진이 두 번 나오면 한 번만 올린다")
    void uploadsSameImageOnce() throws IOException {
        given(fetcher.fetch(DC, SOURCE)).willReturn(DcImageFetcher.detect(JPG));
        given(imageService.preparePostImage(any())).willReturn(prepared);
        given(lifecycle.upload(prepared, OWNER))
                .willReturn(new ImageUploadResponse("https://images.snowthing.org/n.jpg", "n"));

        String result = images.replace("![](" + DC + ")\n![a](" + DC + ")", SOURCE, OWNER);

        assertThat(result)
                .isEqualTo(
                        "![](https://images.snowthing.org/n.jpg)\n![a](https://images.snowthing.org/n.jpg)");
        verify(fetcher, times(1)).fetch(DC, SOURCE);
    }

    @Test
    @DisplayName("gif처럼 눈팅이 받지 않는 사진은 원문 링크로 바꾼다")
    void unsupportedImageBecomesSourceLink() throws IOException {
        given(fetcher.fetch(DC, SOURCE)).willReturn(DcImageFetcher.detect("GIF89a".getBytes()));

        String result = images.replace("![](" + DC + ")", SOURCE, OWNER);

        assertThat(result).isEqualTo("[사진은 원문에서 보기](" + SOURCE + ")");
        verify(lifecycle, never()).upload(any(ImageService.PreparedPostImage.class), anyString());
    }

    @Test
    @DisplayName("크기나 해상도 검사에 걸린 사진도 원문 링크로 바꾼다")
    void rejectedImageBecomesSourceLink() throws IOException {
        given(fetcher.fetch(DC, SOURCE)).willReturn(DcImageFetcher.detect(JPG));
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
        verify(fetcher, never()).fetch(anyString(), eq(SOURCE));
    }

    @Test
    @DisplayName("사진을 받지 못하면 예외를 던져 다음 게시 시각에 다시 시도하게 한다")
    void networkFailurePropagates() throws IOException {
        given(fetcher.fetch(DC, SOURCE)).willThrow(new IOException("timeout"));

        assertThatThrownBy(() -> images.replace("![](" + DC + ")", SOURCE, OWNER))
                .isInstanceOf(IOException.class);
    }
}
