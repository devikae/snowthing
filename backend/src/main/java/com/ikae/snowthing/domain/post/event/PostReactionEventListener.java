package com.ikae.snowthing.domain.post.event;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.ReactionType;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostReactionEventListener {

    private final PostRepository postRepository;

    @Async
    @EventListener
    @Transactional
    public void handlePostReactionEvent(PostReactionEvent event) {
        log.info("비동기 추천/비추천 수 카운트 갱신 처리 - postId: {}, type: {}", event.postId(), event.type());
        Post post = postRepository.findById(event.postId()).orElse(null);
        if (post == null) {
            log.warn("해당 게시글을 찾을 수 없습니다. postId: {}", event.postId());
            return;
        }

        if (event.type() == ReactionType.LIKE) {
            post.increaseLikeCount();
        } else if (event.type() == ReactionType.DISLIKE) {
            post.increaseDislikeCount();
        }
    }
}
