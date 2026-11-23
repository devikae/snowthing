package com.ikae.snowthing.domain.post.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.post.entity.PostCategory;

public interface PostCategoryRepository extends JpaRepository<PostCategory, Long> {

    Optional<PostCategory> findByCode(String code);

    /** 게시판은 20줄 안쪽이라 전부 읽어 메모리에서 거른다. 순서가 같으면 ID 순. */
    List<PostCategory> findAllByOrderBySortOrderAscIdAsc();
}
