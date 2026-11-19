package com.ikae.snowthing.domain.contentfilter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.contentfilter.entity.BannedWord;

public interface BannedWordRepository extends JpaRepository<BannedWord, Long> {

    List<BannedWord> findAllByOrderByCreatedAtDescIdDesc();

    boolean existsByNormalizedWord(String normalizedWord);
}
