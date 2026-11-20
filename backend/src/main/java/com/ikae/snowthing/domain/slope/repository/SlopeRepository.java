package com.ikae.snowthing.domain.slope.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.member.entity.ResortCountry;
import com.ikae.snowthing.domain.slope.entity.Slope;

public interface SlopeRepository extends JpaRepository<Slope, Long> {

    /** 슬로프 제원 화면에 보일 리조트. Resort.isSlopeSpecVisible()과 같은 규칙이다. */
    String VISIBLE =
            " (r.active = true OR r.country <> com.ikae.snowthing.domain.member.entity.ResortCountry.KR)";

    /** 리조트 여러 곳의 슬로프. idx_slope_resort_order를 탄다. */
    @Query(
            "SELECT s FROM Slope s JOIN FETCH s.resort r"
                    + " WHERE r.id IN :resortIds ORDER BY r.displayOrder, s.displayOrder, s.id")
    List<Slope> findAllByResortIdIn(@Param("resortIds") List<Long> resortIds);

    @Query("SELECT s FROM Slope s JOIN FETCH s.resort r WHERE s.id = :id AND" + VISIBLE)
    Optional<Slope> findActiveWithResort(@Param("id") Long id);

    /** 비슷한 슬로프 후보. 수백 건 수준이라 한 번에 읽어 메모리에서 비교한다. */
    @Query(
            "SELECT s FROM Slope s JOIN FETCH s.resort r"
                    + " WHERE r.country = :country AND"
                    + VISIBLE
                    + " ORDER BY r.displayOrder, s.displayOrder, s.id")
    List<Slope> findAllActiveByCountry(@Param("country") ResortCountry country);

    /** 보이는 리조트별 슬로프 수. [resortId, count] 배열. */
    @Query("SELECT r.id, COUNT(s) FROM Slope s JOIN s.resort r WHERE" + VISIBLE + " GROUP BY r.id")
    List<Object[]> countByActiveResort();
}
