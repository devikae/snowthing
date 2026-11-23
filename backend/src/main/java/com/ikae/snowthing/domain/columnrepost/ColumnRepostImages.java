package com.ikae.snowthing.domain.columnrepost;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.ikae.snowthing.domain.image.service.ImageService;
import com.ikae.snowthing.domain.image.service.PostImageLifecycleService;
import com.ikae.snowthing.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 본문의 디시 사진을 눈팅 사진으로 바꾼다. 회원이 사진을 올릴 때와 같은 준비(크기 조정, 형식 검사)와 업로드 기록을 거쳐, 글이 지워지면 사진도 같이 정리된다.
 *
 * <p>게시하는 순간에 올려야 한다. 업로드한 사진은 1시간 안에 글에 연결되지 않으면 정리 대상이 된다.
 */
@Component
@RequiredArgsConstructor
public class ColumnRepostImages {

    static final String ORIGINAL_LINK_TEXT = "사진은 원문에서 보기";
    private static final Pattern MARKDOWN_IMAGE =
            Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)");

    private final DcImageFetcher fetcher;
    private final ImageService imageService;
    private final PostImageLifecycleService lifecycle;

    /**
     * @param sourceUrl 원문 글 주소. Referer와 대체 링크에 쓴다.
     * @param owner 업로드 기록의 주인(글쓴이 이메일). 글을 쓸 때 같은 주인인지 확인한다.
     * @throws IOException 사진을 받지 못했을 때
     */
    public String replace(String content, String sourceUrl, String owner) throws IOException {
        Map<String, String> replaced = new LinkedHashMap<>();
        Matcher matcher = MARKDOWN_IMAGE.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group(2);
            String replacement;
            if (!DcImageFetcher.isAllowed(url)) {
                replacement = matcher.group();
            } else {
                String uploaded = replaced.get(url);
                if (uploaded == null) {
                    uploaded = upload(url, sourceUrl, owner);
                    replaced.put(url, uploaded);
                }
                replacement =
                        uploaded.isEmpty()
                                ? "[" + ORIGINAL_LINK_TEXT + "](" + sourceUrl + ")"
                                : "![" + matcher.group(1) + "](" + uploaded + ")";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * @return 눈팅 사진 주소. 눈팅이 받지 않는 사진(gif, 5MB 초과, 너무 큰 해상도)이면 빈 문자열
     */
    private String upload(String url, String sourceUrl, String owner) throws IOException {
        DcImageFetcher.Fetched fetched = fetcher.fetch(url, sourceUrl);
        if (!fetched.supported()) {
            return "";
        }
        ImageService.PreparedPostImage prepared;
        try {
            prepared = imageService.preparePostImage(new BytesFile(fetched));
        } catch (BusinessException unsupported) {
            return "";
        }
        return lifecycle.upload(prepared, owner).imageUrl();
    }

    /** 받은 사진을 회원 업로드와 같은 준비 과정에 넣기 위한 파일 모양. */
    private record BytesFile(DcImageFetcher.Fetched fetched) implements MultipartFile {
        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return "column." + fetched.extension();
        }

        @Override
        public String getContentType() {
            return fetched.contentType();
        }

        @Override
        public boolean isEmpty() {
            return fetched.bytes().length == 0;
        }

        @Override
        public long getSize() {
            return fetched.bytes().length;
        }

        @Override
        public byte[] getBytes() {
            return fetched.bytes();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(fetched.bytes());
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), fetched.bytes());
        }
    }
}
