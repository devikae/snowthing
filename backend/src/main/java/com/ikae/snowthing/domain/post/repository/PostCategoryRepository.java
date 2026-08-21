package com.ikae.snowthing.domain.post.repository;

import com.ikae.snowthing.domain.post.entity.PostCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostCategoryRepository extends JpaRepository<PostCategory, Long> {

    Optional<PostCategory> findByCode(String code);
}
