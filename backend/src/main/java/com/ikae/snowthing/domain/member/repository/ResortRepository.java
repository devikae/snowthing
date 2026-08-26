package com.ikae.snowthing.domain.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.member.entity.Resort;

public interface ResortRepository extends JpaRepository<Resort, Long> {
    Optional<Resort> findByName(String name);
}
