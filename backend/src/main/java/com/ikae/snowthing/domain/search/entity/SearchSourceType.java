package com.ikae.snowthing.domain.search.entity;

public enum SearchSourceType {
    POST("P"),
    COMMENT("C");

    private final String evidencePrefix;

    SearchSourceType(String evidencePrefix) {
        this.evidencePrefix = evidencePrefix;
    }

    public String evidencePrefix() {
        return evidencePrefix;
    }

    public static SearchSourceType fromEvidencePrefix(String prefix) {
        for (SearchSourceType type : values()) {
            if (type.evidencePrefix.equals(prefix)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown evidence prefix");
    }
}
