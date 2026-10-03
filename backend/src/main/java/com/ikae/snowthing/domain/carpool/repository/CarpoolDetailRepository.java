package com.ikae.snowthing.domain.carpool.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.post.entity.PostStatus;

public interface CarpoolDetailRepository extends JpaRepository<CarpoolDetail, Long> {

    Optional<CarpoolDetail> findByPostPublicId(String publicId);

    Page<CarpoolDetail> findByPostStatusAndPostIsDeletedFalse(PostStatus status, Pageable pageable);
}
