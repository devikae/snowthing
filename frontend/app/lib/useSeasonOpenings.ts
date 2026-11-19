"use client";

import { useEffect, useState } from "react";
import { API_ENDPOINTS } from "./api";
import type { SeasonOpenings } from "./seasonOpening";

let pending: Promise<SeasonOpenings | null> | null = null;

/**
 * 개장일 공개 API를 한 번만 부른다. 한 화면에서 여러 컴포넌트(슬로프 카드 여러 장 등)가 써도 요청은 하나다.
 * 실패하면 null을 주고 다음 호출 때 다시 묻는다. 페이지를 새로고침하면 새로 받는다.
 */
export function fetchSeasonOpenings(): Promise<SeasonOpenings | null> {
  if (!pending) {
    pending = fetch(API_ENDPOINTS.public.seasonOpenings, { cache: "no-store" })
      .then(async (response) => (response.ok ? ((await response.json()) as SeasonOpenings) : null))
      .catch(() => null)
      .then((data) => {
        if (!data) pending = null;
        return data;
      });
  }
  return pending;
}

/** 관리자가 저장한 뒤 부른다. 같은 탭에서 다른 화면으로 옮겨도 새 값을 받게 한다. */
export function clearSeasonOpeningsCache() {
  pending = null;
}

/** 개장일 목록. 받기 전이나 실패하면 null이라, 쓰는 쪽은 D-DAY만 그리지 않으면 된다. */
export function useSeasonOpenings(): SeasonOpenings | null {
  const [data, setData] = useState<SeasonOpenings | null>(null);

  useEffect(() => {
    let alive = true;
    void fetchSeasonOpenings().then((next) => {
      if (alive) setData(next);
    });
    return () => {
      alive = false;
    };
  }, []);

  return data;
}
