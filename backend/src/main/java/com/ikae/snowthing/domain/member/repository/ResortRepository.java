package com.ikae.snowthing.domain.member.repository;

import com.ikae.snowthing.domain.member.entity.Resort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResortRepository extends JpaRepository<Resort, Long> {
    Optional<Resort> findByName(String name);
}
