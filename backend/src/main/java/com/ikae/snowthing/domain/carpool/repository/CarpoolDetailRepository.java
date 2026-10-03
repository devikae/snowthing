package com.ikae.snowthing.domain.carpool.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;

public interface CarpoolDetailRepository extends JpaRepository<CarpoolDetail, Long> {

    Optional<CarpoolDetail> findByPostPublicId(String publicId);
}
