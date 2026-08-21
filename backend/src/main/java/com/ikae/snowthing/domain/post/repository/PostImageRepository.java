package com.ikae.snowthing.domain.post.repository;

import com.ikae.snowthing.domain.post.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPostIdOrderBySortOrderAsc(Long postId);
}
