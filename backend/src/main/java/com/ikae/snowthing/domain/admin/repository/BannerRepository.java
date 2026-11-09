package com.ikae.snowthing.domain.admin.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.admin.entity.Banner;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findAllByOrderByDisplayOrderAscCreatedAtDesc();

    List<Banner> findByIsActiveTrueOrderByDisplayOrderAscCreatedAtDesc();
}
