package com.ikae.snowthing.domain.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminPostResponse;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPostService {

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public Page<AdminPostResponse> getPosts(boolean deletedOnly, Pageable pageable) {
        Page<PostRepository.AdminPostProjection> posts =
                deletedOnly
                        ? postRepository.findDeletedForAdmin(pageable)
                        : postRepository.findAllForAdmin(pageable);
        return posts.map(AdminPostResponse::from);
    }

    @Transactional
    public void toggleBlindPost(String publicId, boolean blind) {
        int updated = postRepository.updateBlindedByPublicId(publicId, blind);
        if (updated == 0) {
            throw new CustomException(ErrorCode.POST_NOT_FOUND);
        }
        log.info("Post blind status updated by admin: publicId={}, blinded={}", publicId, blind);
    }
}
