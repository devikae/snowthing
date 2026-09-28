package com.ikae.snowthing.domain.post.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.post.entity.PostImage;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPostIdOrderBySortOrderAsc(Long postId);

    @Query(
            "SELECT pi FROM PostImage pi WHERE pi.post.id IN :postIds ORDER BY pi.post.id ASC, pi.sortOrder ASC")
    List<PostImage> findAllByPostIdsOrderBySortOrder(@Param("postIds") List<Long> postIds);
}
