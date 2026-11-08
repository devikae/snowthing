package com.ikae.snowthing.domain.slopestatus.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.slopestatus.entity.ResortSlopeStatus;

public interface ResortSlopeStatusRepository extends JpaRepository<ResortSlopeStatus, Long> {

    Optional<ResortSlopeStatus> findByResortId(Long resortId);

    List<ResortSlopeStatus> findAllByResortIdIn(Collection<Long> resortIds);
}
