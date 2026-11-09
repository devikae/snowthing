package com.ikae.snowthing.domain.post.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.post.entity.PostScrap;

public interface PostScrapRepository
        extends JpaRepository<PostScrap, Long>, PostScrapRepositoryCustom {

    /** 이미 스크랩한 글이면 아무것도 바꾸지 않는다. 동시에 두 번 와도 UNIQUE 위반 예외 없이 한 줄만 남는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "INSERT INTO post_scrap (member_id, post_id, created_at)"
                            + " VALUES (:memberId, :postId, :now)"
                            + " ON DUPLICATE KEY UPDATE post_scrap_id = post_scrap_id",
            nativeQuery = true)
    int insertIgnoringDuplicate(
            @Param("memberId") Long memberId,
            @Param("postId") Long postId,
            @Param("now") LocalDateTime now);

    /** 글 상태와 관계없이 내 스크랩 행만 지운다. 없는 글이어도 0을 돌려줄 뿐이다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "DELETE s FROM post_scrap s JOIN post p ON p.post_id = s.post_id"
                            + " WHERE s.member_id = :memberId AND p.public_id = :postPublicId",
            nativeQuery = true)
    int deleteByMemberAndPostPublicId(
            @Param("memberId") Long memberId, @Param("postPublicId") String postPublicId);

    @Query(
            "SELECT COUNT(s) > 0 FROM PostScrap s"
                    + " WHERE s.member.id = :memberId AND s.post.publicId = :postPublicId")
    boolean existsByMemberAndPostPublicId(
            @Param("memberId") Long memberId, @Param("postPublicId") String postPublicId);
}
