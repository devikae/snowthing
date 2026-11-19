package com.ikae.snowthing.domain.slope.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.member.entity.ResortCountry;
import com.ikae.snowthing.domain.slope.entity.Slope;

public interface SlopeRepository extends JpaRepository<Slope, Long> {

    /** 리조트 여러 곳의 슬로프. idx_slope_resort_order를 탄다. */
    @Query(
            "SELECT s FROM Slope s JOIN FETCH s.resort r"
                    + " WHERE r.id IN :resortIds ORDER BY r.displayOrder, s.displayOrder, s.id")
    List<Slope> findAllByResortIdIn(@Param("resortIds") List<Long> resortIds);

    @Query("SELECT s FROM Slope s JOIN FETCH s.resort r WHERE s.id = :id AND r.active = true")
    Optional<Slope> findActiveWithResort(@Param("id") Long id);

    /** 비슷한 슬로프 후보. 수백 건 수준이라 한 번에 읽어 메모리에서 비교한다. */
    @Query(
            "SELECT s FROM Slope s JOIN FETCH s.resort r"
                    + " WHERE r.active = true AND r.country = :country"
                    + " ORDER BY r.displayOrder, s.displayOrder, s.id")
    List<Slope> findAllActiveByCountry(@Param("country") ResortCountry country);

    /** 활성 리조트별 슬로프 수. [resortId, count] 배열. */
    @Query(
            "SELECT s.resort.id, COUNT(s) FROM Slope s WHERE s.resort.active = true"
                    + " GROUP BY s.resort.id")
    List<Object[]> countByActiveResort();
}
