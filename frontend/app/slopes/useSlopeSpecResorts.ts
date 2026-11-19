"use client";

import { useEffect, useState } from "react";
import { API_ENDPOINTS } from "../lib/api";
import type { ResortSummary } from "./specs";

// 슬로프 제원 페이지가 있는 리조트 목록. 슬로프캠, 현황판처럼 여러 화면이 같은 목록을 쓰므로 한 번만 받는다.
let pending: Promise<ResortSummary[]> | null = null;

function load() {
  if (!pending) {
    pending = fetch(API_ENDPOINTS.slopeSpecs.resorts)
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then((data: { resorts?: ResortSummary[] }) => (Array.isArray(data.resorts) ? data.resorts : []))
      .catch(() => {
        // 실패하면 다음 화면에서 다시 시도한다. 버튼만 안 보이고 화면은 그대로 쓴다.
        pending = null;
        return [];
      });
  }
  return pending;
}

export function useSlopeSpecResorts() {
  const [resorts, setResorts] = useState<ResortSummary[]>([]);
  useEffect(() => {
    let alive = true;
    load().then((list) => {
      if (alive) setResorts(list);
    });
    return () => {
      alive = false;
    };
  }, []);
  return resorts;
}
