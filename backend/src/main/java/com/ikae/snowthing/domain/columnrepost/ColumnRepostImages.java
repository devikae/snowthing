package com.ikae.snowthing.domain.columnrepost;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.ikae.snowthing.domain.image.service.ImageService;
import com.ikae.snowthing.domain.image.service.PostImageLifecycleService;
import com.ikae.snowthing.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 본문의 디시 미디어를 눈팅으로 옮긴다.
 *
 * <ul>
 *   <li>jpg, png, webp 사진: 회원이 사진을 올릴 때와 같은 준비(크기 조정, 형식 검사)와 업로드 기록을 거친다. 글이 지워지면 같이 정리된다.
 *   <li>GIF, GIF를 바꾼 mp4 클립, 동영상 플레이어 영상: 고치지 않고 그대로 올린다({@link ColumnMediaStorage}).
 * </ul>
 *
 * <p>게시하는 순간에 올려야 한다. 업로드한 사진은 1시간 안에 글에 연결되지 않으면 정리 대상이 되고, 동영상 주소의 서명 키도 오래 가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ColumnRepostImages {

    static final String ORIGINAL_LINK_TEXT = "사진은 원문에서 보기";
    static final String ORIGINAL_VIDEO_LINK_TEXT = "▶ 영상은 원문에서 보기";
    private static final Pattern MARKDOWN_IMAGE =
            Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)");

    private final DcImageFetcher fetcher;
    private final ImageService imageService;
    private final PostImageLifecycleService lifecycle;
    private final ColumnMediaStorage storage;

    /**
     * @param sourceUrl 원문 글 주소. Referer와 대체 링크에 쓴다.
     * @param owner 업로드 기록의 주인(글쓴이 이메일). 글을 쓸 때 같은 주인인지 확인한다.
     * @throws IOException 미디어를 받지 못했을 때
     */
    public String replace(String content, String sourceUrl, String owner) throws IOException {
        Map<String, String> replaced = new LinkedHashMap<>();
        Matcher matcher = MARKDOWN_IMAGE.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group(2);
            boolean movie = DcImageFetcher.isMoviePage(url);
            String replacement;
            if (!movie && !DcImageFetcher.isAllowed(url)) {
                replacement = matcher.group();
            } else {
                String uploaded = replaced.get(url);
                if (uploaded == null) {
                    uploaded = movie ? uploadMovie(url, sourceUrl) : upload(url, sourceUrl, owner);
                    replaced.put(url, uploaded);
                }
                String linkText = movie ? ORIGINAL_VIDEO_LINK_TEXT : ORIGINAL_LINK_TEXT;
                replacement =
                        uploaded.isEmpty()
                                ? "[" + linkText + "](" + sourceUrl + ")"
                                : "![" + matcher.group(1) + "](" + uploaded + ")";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * @return 눈팅 주소. 옮기지 못하는 파일(알 수 없는 형식, 상한 초과, 사진 검사 실패)이면 빈 문자열
     */
    private String upload(String url, String sourceUrl, String owner) throws IOException {
        try (DcImageFetcher.Downloaded media =
                fetcher.download(url, sourceUrl, DcImageFetcher.MAX_MEDIA_BYTES)) {
            if (media.tooLarge()) return "";
            DcImageFetcher.Kind kind = media.kind();
            if (kind == DcImageFetcher.Kind.GIF) {
                return storage.upload(media.file(), ColumnMediaStorage.Use.GIF, kind);
            }
            if (kind == DcImageFetcher.Kind.MP4) {
                return storage.upload(media.file(), ColumnMediaStorage.Use.CLIP, kind);
            }
            if (!kind.isStillImage() || media.size() > DcImageFetcher.MAX_IMAGE_BYTES) return "";
            ImageService.PreparedPostImage prepared;
            try {
                prepared = imageService.preparePostImage(new BytesFile(kind, media.bytes()));
            } catch (BusinessException unsupported) {
                return "";
            }
            return lifecycle.upload(prepared, owner).imageUrl();
        }
    }

    /**
     * @return 눈팅 영상 주소. 페이지에 영상이 없거나 mp4가 아니거나 상한을 넘으면 빈 문자열
     */
    private String uploadMovie(String pageUrl, String sourceUrl) throws IOException {
        Optional<String> fileUrl = fetcher.movieFileUrl(pageUrl, sourceUrl);
        if (fileUrl.isEmpty()) return "";
        try (DcImageFetcher.Downloaded movie =
                fetcher.download(fileUrl.get(), pageUrl, DcImageFetcher.MAX_MEDIA_BYTES)) {
            if (movie.tooLarge() || movie.kind() != DcImageFetcher.Kind.MP4) return "";
            return storage.upload(movie.file(), ColumnMediaStorage.Use.MOVIE, movie.kind());
        }
    }

    /** 받은 사진을 회원 업로드와 같은 준비 과정에 넣기 위한 파일 모양. */
    private record BytesFile(DcImageFetcher.Kind kind, byte[] bytes) implements MultipartFile {
        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return "column." + kind.extension();
        }

        @Override
        public String getContentType() {
            return kind.contentType();
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), bytes);
        }
    }
}
