package com.ikae.snowthing.domain.slopestatus.source;

import java.util.List;

/**
 * 한 번 읽은 리조트 현황.
 *
 * @param operatingNote 출처가 표 머리 등에 적은 운영 시간 문구. 없으면 null
 */
public record SlopeSnapshot(List<SlopeEntry> slopes, String operatingNote) {

    public SlopeSnapshot {
        slopes = List.copyOf(slopes);
    }

    public int openCount() {
        return (int) slopes.stream().filter(SlopeEntry::hasOpenSession).count();
    }
}
