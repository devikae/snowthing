package com.ikae.snowthing.domain.contentfilter.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.contentfilter.entity.BannedWord;

public record BannedWordResponse(Long id, String word, LocalDateTime createdAt) {

    public static BannedWordResponse from(BannedWord word) {
        return new BannedWordResponse(word.getId(), word.getWord(), word.getCreatedAt());
    }
}
