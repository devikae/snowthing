package com.ikae.snowthing.domain.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.ResortCountry;

public interface ResortRepository extends JpaRepository<Resort, Long> {
    Optional<Resort> findByName(String name);

    Optional<Resort> findByCode(String code);

    List<Resort> findAllByActiveTrueAndCountryOrderByDisplayOrderAscIdAsc(ResortCountry country);

    /** 커뮤니티 기능용 리조트 목록. 활성 국내 리조트만 표시 순서대로 준다. */
    default List<Resort> findServiceResorts() {
        return findAllByActiveTrueAndCountryOrderByDisplayOrderAscIdAsc(ResortCountry.KR);
    }
}
