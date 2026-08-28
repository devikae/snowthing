package com.ikae.snowthing.domain.member.dto;

import com.ikae.snowthing.domain.member.entity.Resort;

public record ResortResponse(Long id, String name, String regionName) {
    public static ResortResponse from(Resort resort) {
        return new ResortResponse(resort.getId(), resort.getName(), resort.getRegionName());
    }
}
