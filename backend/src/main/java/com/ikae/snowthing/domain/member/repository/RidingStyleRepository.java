package com.ikae.snowthing.domain.member.repository;

import com.ikae.snowthing.domain.member.entity.RidingStyle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RidingStyleRepository extends JpaRepository<RidingStyle, Long> {
    Optional<RidingStyle> findByStyleName(String styleName);
}
