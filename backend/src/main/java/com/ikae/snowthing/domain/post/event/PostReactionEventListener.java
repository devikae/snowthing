package com.ikae.snowthing.domain.post.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.post.repository.PostRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostReactionEventListener {

    private final PostRepository postRepository;

    @Async
    @EventListener
    @Transactional
    public void handlePostReactionEvent(PostReactionEvent event) {
        log.info("비동기 추천/비추천 이벤트 알림 기록 - postId: {}, type: {}", event.postId(), event.type());
    }
}
