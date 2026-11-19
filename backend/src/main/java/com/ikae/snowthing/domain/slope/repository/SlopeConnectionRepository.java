package com.ikae.snowthing.domain.slope.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.slope.entity.SlopeConnection;

public interface SlopeConnectionRepository extends JpaRepository<SlopeConnection, Long> {

    @Query(
            "SELECT c FROM SlopeConnection c JOIN FETCH c.relatedSlope"
                    + " WHERE c.slope.id IN :slopeIds ORDER BY c.slope.id, c.id")
    List<SlopeConnection> findAllBySlopeIdIn(@Param("slopeIds") List<Long> slopeIds);
}
