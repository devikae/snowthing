package com.ikae.snowthing.domain.chat.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResortTag {
    PHOENIX("휘닉스", "휘팍"),
    VIVALDI("비발디", "비발"),
    HIGH1("하이원", "하이"),
    YONGPYONG("용평", "용평"),
    WELLI_HILLI("웰팍", "웰팍"),
    ETC("기타", "기타");

    private final String koreanName;
    private final String shortName;

    public static ResortTag from(String rawTag) {
        if (rawTag == null || rawTag.isBlank()) {
            return null;
        }
        for (ResortTag tag : values()) {
            if (tag.name().equalsIgnoreCase(rawTag.trim())
                    || tag.koreanName.equalsIgnoreCase(rawTag.trim())
                    || tag.shortName.equalsIgnoreCase(rawTag.trim())) {
                return tag;
            }
        }
        return null;
    }
}
