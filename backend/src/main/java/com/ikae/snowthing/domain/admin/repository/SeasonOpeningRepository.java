package com.ikae.snowthing.domain.admin.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.admin.entity.SeasonOpening;

public interface SeasonOpeningRepository extends JpaRepository<SeasonOpening, Long> {

    List<SeasonOpening> findAllByOrderByBannerOrderAscIdAsc();
}
