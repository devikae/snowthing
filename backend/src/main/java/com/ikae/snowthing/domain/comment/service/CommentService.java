package com.ikae.snowthing.domain.comment.service;

import com.ikae.snowthing.domain.comment.dto.*;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CommentResponse createComment(String postPublicId, CommentCreateRequest request,
                                         CustomUserDetails userDetails, String clientIp) {
        Post post = postRepository.findByPublicId(postPublicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        Comment parent = null;
        if (request.parentId() != null) {
            parent = commentRepository.findById(request.parentId())
                .orElseThrow(() -> new CustomAuthException(ErrorCode.PARENT_COMMENT_NOT_FOUND));

            if (!parent.getPost().getId().equals(post.getId())) {
                throw new CustomAuthException(ErrorCode.INVALID_COMMENT_PARENT);
            }
        }

        Member member = null;
        String encodedPassword = null;

        if (request.isAnonymous()) {
            if (request.anonymousPassword() == null || request.anonymousPassword().isBlank()) {
                throw new CustomAuthException(ErrorCode.INVALID_INPUT);
            }
            encodedPassword = passwordEncoder.encode(request.anonymousPassword());
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
            }
            member = memberRepository.findByPublicId(userDetails.getPublicId())
                .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
        }

        Comment comment = Comment.builder()
            .post(post)
            .member(member)
            .parent(parent)
            .content(request.content())
            .writerIp(clientIp != null ? clientIp : "127.0.0.1")
            .isAnonymous(request.isAnonymous())
            .anonymousPassword(encodedPassword)
            .build();

        Comment savedComment = commentRepository.save(comment);
        post.increaseCommentCount();

        return CommentResponse.from(savedComment);
    }

    public PostCommentListResponse getCommentsByPost(String postPublicId) {
        Post post = postRepository.findByPublicId(postPublicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        List<Comment> comments = commentRepository.findByPostIdWithMember(post.getId());

        Map<Long, CommentResponse> map = new LinkedHashMap<>();
        List<CommentResponse> rootComments = new ArrayList<>();

        for (Comment comment : comments) {
            CommentResponse dto = CommentResponse.from(comment);
            map.put(dto.commentId(), dto);

            if (dto.parentId() == null) {
                rootComments.add(dto);
            } else {
                CommentResponse parentDto = map.get(dto.parentId());
                if (parentDto != null) {
                    parentDto.children().add(dto);
                }
            }
        }

        return PostCommentListResponse.builder()
            .publicId(postPublicId)
            .totalCommentCount(post.getCommentCount())
            .comments(rootComments)
            .build();
    }

    @Transactional
    public void deleteComment(Long commentId, String anonymousPassword, CustomUserDetails userDetails) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted()) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }

        validateDeletePermission(comment, anonymousPassword, userDetails);

        comment.softDelete();
        comment.getPost().decreaseCommentCount();
    }

    private void validateDeletePermission(Comment comment, String anonymousPassword, CustomUserDetails userDetails) {
        boolean isAdmin = userDetails != null && userDetails.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return; // ROLE_ADMIN 최고 관리자는 비밀번호 검증 없이 모든 댓글(익명/회원) 삭제 가능
        }

        if (comment.isAnonymous()) {
            if (userDetails != null && comment.getMember() != null &&
                comment.getMember().getPublicId().equals(userDetails.getPublicId())) {
                return; // 로그인한 작성 본인은 비밀번호 없이 삭제 가능
            }

            if (anonymousPassword == null || !passwordEncoder.matches(anonymousPassword, comment.getAnonymousPassword())) {
                throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
            }
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }

            boolean isWriter = comment.getMember() != null && comment.getMember().getPublicId().equals(userDetails.getPublicId());

            if (!isWriter) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
        }
    }
}
