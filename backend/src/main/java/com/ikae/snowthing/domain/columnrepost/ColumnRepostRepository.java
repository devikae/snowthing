package com.ikae.snowthing.domain.columnrepost;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ColumnRepostRepository extends JpaRepository<ColumnRepostItem, Long> {

    /** 게시 순서가 가장 앞선 대기 글. */
    Optional<ColumnRepostItem> findFirstByStatusOrderBySequenceNoAscIdAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ColumnRepostItem c WHERE c.id = :id")
    Optional<ColumnRepostItem> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(String status);

    Optional<ColumnRepostItem> findBySequenceNo(Integer sequenceNo);
}
