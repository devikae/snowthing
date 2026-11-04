package com.ikae.snowthing.domain.search.dto;

import java.util.List;

public record EvidenceSearchResponse(
        String query, int size, List<EvidenceSearchItemResponse> items) {

    public EvidenceSearchResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
