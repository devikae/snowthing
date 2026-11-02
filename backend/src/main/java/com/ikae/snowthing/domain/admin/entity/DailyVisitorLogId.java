package com.ikae.snowthing.domain.admin.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DailyVisitorLogId implements Serializable {

    @Column(name = "visit_date")
    private LocalDate visitDate;

    @Column(name = "visitor_hash", length = 64)
    private String visitorHash;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyVisitorLogId that = (DailyVisitorLogId) o;
        return Objects.equals(visitDate, that.visitDate)
                && Objects.equals(visitorHash, that.visitorHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(visitDate, visitorHash);
    }
}
