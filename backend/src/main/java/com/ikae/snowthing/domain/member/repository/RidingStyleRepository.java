package com.ikae.snowthing.domain.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.member.entity.RidingStyle;

public interface RidingStyleRepository extends JpaRepository<RidingStyle, Long> {
    Optional<RidingStyle> findByStyleName(String styleName);
}
