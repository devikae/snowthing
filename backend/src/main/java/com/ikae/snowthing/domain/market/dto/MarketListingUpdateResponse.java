package com.ikae.snowthing.domain.market.dto;

import java.time.LocalDateTime;

public record MarketListingUpdateResponse(String publicId, long version, LocalDateTime updatedAt) {}
