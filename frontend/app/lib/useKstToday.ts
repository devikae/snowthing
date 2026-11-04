"use client";

import { useSyncExternalStore } from "react";
import { kstDateKey } from "./seasonOpening";

const subscribe = () => () => {};

/**
 * KST 오늘 날짜(YYYY-MM-DD). 서버 렌더와 하이드레이션 중에는 null이라, 자정 근처에 서버와 브라우저의 "오늘"이 달라도
 * 하이드레이션 불일치가 생기지 않는다.
 */
export function useKstToday(): string | null {
  return useSyncExternalStore(
    subscribe,
    () => kstDateKey(new Date()),
    () => null,
  );
}
