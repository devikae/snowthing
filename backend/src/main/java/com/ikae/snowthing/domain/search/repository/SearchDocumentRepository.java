package com.ikae.snowthing.domain.search.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.search.entity.SearchDocument;

public interface SearchDocumentRepository extends JpaRepository<SearchDocument, Long> {

    Optional<SearchDocument> findByEvidenceId(String evidenceId);
}
