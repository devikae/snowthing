package com.ikae.snowthing.domain.post.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.post.entity.PostReaction;

public interface PostReactionRepository extends JpaRepository<PostReaction, Long> {

    boolean existsByPostIdAndMemberId(Long postId, Long memberId);

    boolean existsByPostIdAndAnonymousVoterId(Long postId, String anonymousVoterId);

    boolean existsByPostIdAndMemberIdAndType(
            Long postId, Long memberId, com.ikae.snowthing.domain.post.entity.ReactionType type);

    Optional<PostReaction> findByPostIdAndMemberIdAndType(
            Long postId, Long memberId, com.ikae.snowthing.domain.post.entity.ReactionType type);

    Optional<PostReaction> findByPostIdAndAnonymousVoterIdAndType(
            Long postId,
            String anonymousVoterId,
            com.ikae.snowthing.domain.post.entity.ReactionType type);
}
