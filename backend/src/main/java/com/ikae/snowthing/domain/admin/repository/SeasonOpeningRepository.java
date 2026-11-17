package com.ikae.snowthing.domain.admin.repository;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.ikae.snowthing.domain.admin.entity.SeasonOpening;

public interface SeasonOpeningRepository extends JpaRepository<SeasonOpening, Long> {

    List<SeasonOpening> findAllByOrderByBannerOrderAscIdAsc();

    /** 저장용. 동시에 두 저장이 들어오면 줄 잠금으로 한 줄로 세워, 두 요청이 섞인 순서가 남지 않게 한다. 공개 조회에는 쓰지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SeasonOpening> findAllForUpdateByOrderByBannerOrderAscIdAsc();
}
