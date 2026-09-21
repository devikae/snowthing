package com.ikae.snowthing.domain.post.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.post.entity.PostReaction;
import com.ikae.snowthing.domain.post.entity.ReactionType;

public interface PostReactionRepository extends JpaRepository<PostReaction, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "INSERT IGNORE INTO post_reaction (post_id, member_id, writer_ip, anonymous_voter_id, type, created_at, updated_at) VALUES (:postId, :memberId, :writerIp, NULL, :type, NOW(), NOW())",
            nativeQuery = true)
    int insertMemberReaction(
            @Param("postId") Long postId,
            @Param("memberId") Long memberId,
            @Param("writerIp") String writerIp,
            @Param("type") String type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "INSERT IGNORE INTO post_reaction (post_id, member_id, writer_ip, anonymous_voter_id, type, created_at, updated_at) VALUES (:postId, NULL, :writerIp, :anonymousVoterId, :type, NOW(), NOW())",
            nativeQuery = true)
    int insertAnonymousReaction(
            @Param("postId") Long postId,
            @Param("anonymousVoterId") String anonymousVoterId,
            @Param("writerIp") String writerIp,
            @Param("type") String type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "DELETE FROM PostReaction r WHERE r.post.id = :postId AND r.member.id = :memberId AND r.type = :type")
    int deleteMemberReaction(
            @Param("postId") Long postId,
            @Param("memberId") Long memberId,
            @Param("type") ReactionType type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "DELETE FROM PostReaction r WHERE r.post.id = :postId AND r.anonymousVoterId = :anonymousVoterId AND r.type = :type")
    int deleteAnonymousReaction(
            @Param("postId") Long postId,
            @Param("anonymousVoterId") String anonymousVoterId,
            @Param("type") ReactionType type);

    @Query(
            "SELECT r.type FROM PostReaction r WHERE r.post.id = :postId AND r.member.id = :memberId")
    Set<ReactionType> findTypesByPostIdAndMemberId(
            @Param("postId") Long postId, @Param("memberId") Long memberId);

    @Query(
            "SELECT r.type FROM PostReaction r WHERE r.post.id = :postId AND r.anonymousVoterId = :anonymousVoterId")
    Set<ReactionType> findTypesByPostIdAndAnonymousVoterId(
            @Param("postId") Long postId, @Param("anonymousVoterId") String anonymousVoterId);

    long countByPostIdAndType(Long postId, ReactionType type);
}
