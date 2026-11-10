package com.ikae.snowthing.domain.admin.dto;

import java.util.List;

/** 관리자 목록 공통 응답. 전체 건수를 세지 않고 한 건 더 읽어 다음 쪽 여부만 준다. */
public record AdminPage<T>(List<T> items, int page, int size, boolean hasNext) {

    public static <T> AdminPage<T> of(List<T> fetched, int page, int size) {
        boolean hasNext = fetched.size() > size;
        return new AdminPage<>(
                hasNext ? List.copyOf(fetched.subList(0, size)) : List.copyOf(fetched),
                page,
                size,
                hasNext);
    }
}
