package com.ikae.snowthing.domain.member.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum MemberUniqueConstraint {
    EMAIL(Member.EMAIL_UNIQUE_CONSTRAINT),
    NICKNAME(Member.NICKNAME_UNIQUE_CONSTRAINT);

    private final String databaseName;

    MemberUniqueConstraint(String databaseName) {
        this.databaseName = databaseName;
    }

    public static Optional<MemberUniqueConstraint> fromDatabaseName(String name) {
        if (name == null) return Optional.empty();
        String normalized = name.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(
                        value ->
                                normalized.equals(value.databaseName)
                                        || normalized.endsWith("." + value.databaseName))
                .findFirst();
    }
}
