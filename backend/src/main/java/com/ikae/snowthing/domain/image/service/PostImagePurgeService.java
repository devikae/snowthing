package com.ikae.snowthing.domain.image.service;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.image.dto.PostImagePurgeResponse;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.InquiryAccess;
import com.ikae.snowthing.domain.search.service.SearchOutboxWriter;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 글은 두고 사진만 지우는 관리자 기능. 신고 대응과 이미 삭제된 글의 정리에 쓴다. */
@Service
public class PostImagePurgeService {
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("post.image.audit");

    private final PostRepository posts;
    private final PostImageLifecycleService lifecycle;
    private final SearchOutboxWriter searchOutbox;
    private final TransactionTemplate tx;

    public PostImagePurgeService(
            PostRepository posts,
            PostImageLifecycleService lifecycle,
            SearchOutboxWriter searchOutbox,
            PlatformTransactionManager manager) {
        this.posts = posts;
        this.lifecycle = lifecycle;
        this.searchOutbox = searchOutbox;
        this.tx = new TransactionTemplate(manager);
    }

    public PostImagePurgeResponse purge(String publicId, String reason, CustomUserDetails admin) {
        // URL 규칙이 바뀌어도 관리자만 쓰도록 SecurityConfig와 별개로 한 번 더 막는다.
        if (!InquiryAccess.isSessionUser(admin) || !InquiryAccess.isAdmin(admin)) {
            AUDIT_LOG.warn(
                    "action=PURGE_DENIED post={} actor={}",
                    publicId,
                    admin == null ? "ANONYMOUS" : admin.getPublicId());
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        var released =
                tx.execute(
                        status -> {
                            Post post =
                                    posts.findIncludingDeletedForUpdate(publicId)
                                            .orElseThrow(
                                                    () ->
                                                            new CustomException(
                                                                    ErrorCode.POST_NOT_FOUND));
                            var result = lifecycle.releasePostImages(post);
                            post.update(
                                    post.getTitle(),
                                    withoutImages(post.getContent(), result),
                                    post.getCategory());
                            posts.flush();
                            searchOutbox.postChanged(post);
                            return result;
                        });
        // 커밋 뒤에 남긴다. 롤백된 작업이 기록에 남지 않는다. 사유는 줄바꿈을 지워 기록을 위조하지 못하게 한다.
        AUDIT_LOG.info(
                "action=PURGE post={} actor={} queued={} shared={} reason={}",
                publicId,
                admin.getPublicId(),
                released.queuedKeys(),
                released.sharedKeys(),
                reason.replaceAll("\\p{Cntrl}", " "));
        return new PostImagePurgeResponse(released.queuedKeys(), released.sharedKeys());
    }

    /** 본문에 키를 남기면 파일이 지워진 뒤 작성자가 글을 수정할 때 "지워진 사진을 붙였다"며 거절된다. 공유 키도 이 글에서는 뺀다. */
    private String withoutImages(
            String content, PostImageLifecycleService.ReleasedImages released) {
        // base64 사진은 파일이 아니라 본문에 들어 있어 본문에서 지워야 사라진다.
        String result =
                content.replaceAll("!\\[[^\\]]*\\]\\(\\s*data:image/[^)]*\\)", "")
                        .replaceAll("(?i)<img\\b[^>]*data:image/[^>]*>", "");
        for (var keys : java.util.List.of(released.queuedKeys(), released.sharedKeys())) {
            for (String key : keys) {
                String quoted = Pattern.quote(key);
                result =
                        result.replaceAll("!\\[[^\\]]*\\]\\([^)]*" + quoted + "[^)]*\\)", "")
                                .replaceAll("(?i)<img\\b[^>]*" + quoted + "[^>]*>", "")
                                .replaceAll("(https?://[^\\s)\"'<>]*/)?" + quoted, "");
            }
        }
        return result;
    }
}
