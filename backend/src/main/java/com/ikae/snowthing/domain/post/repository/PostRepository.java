package com.ikae.snowthing.domain.post.repository;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

    Optional<Post> findByPublicId(String publicId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.member JOIN FETCH p.category WHERE p.publicId = :publicId")
    Optional<Post> findWithMemberAndCategoryByPublicId(@Param("publicId") String publicId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.member JOIN FETCH p.category WHERE p.category.code = :categoryCode")
    Page<Post> findByCategoryCodeWithMemberAndCategory(@Param("categoryCode") String categoryCode, Pageable pageable);

    @Query(value = "SELECT p FROM Post p LEFT JOIN FETCH p.member JOIN FETCH p.category",
           countQuery = "SELECT COUNT(p) FROM Post p")
    Page<Post> findAllWithMemberAndCategory(Pageable pageable);
}
