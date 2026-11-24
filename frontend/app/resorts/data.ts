import "server-only";

import { API_V1_URL } from "../lib/api";
import type { SeasonOpenings } from "../lib/seasonOpening";
import { getResortCameras } from "../resort-cam/data";
import { resortSlug, type ResortCameraGroup } from "../resort-cam/types";

// IKA-107 리조트 한눈에 보기. 설계: docs/conception/034_resort_landing/01_design.md
//
// 이 화면은 검색에서 들어오는 공개 페이지라 방문자가 늘어도 백엔드 호출이 같이 늘면 안 된다.
// 로그인 정보가 섞이지 않는 공개 조회만 서버 프로세스 안에 잠깐 보관한다(슬로프캠 목록과 같은 방식).
// 방문자 쿠키는 넘기지 않으므로 보관한 값에 개인 정보(삭제 가능 여부 등)가 들어가지 않는다.
const FETCH_TIMEOUT_MS = 2000;
// 실패한 조회를 요청마다 다시 부르면 백엔드가 느릴 때 모든 방문자가 2초씩 기다린다. 잠깐은 다시 묻지 않는다.
const RETRY_AFTER_FAILURE_MS = 15_000;
// 새 값을 받지 못할 때 예전 값을 대신 보여 주는 최대 시간. 이보다 오래되면 "불러오지 못했습니다"로 둔다.
const MAX_STALE_MS = 30 * 60 * 1000;

type CacheEntry = { at: number; data: unknown; failedAt: number };
const entries = new Map<string, CacheEntry>();
const inflight = new Map<string, Promise<unknown>>();

async function requestJson(path: string): Promise<unknown> {
  try {
    const response = await fetch(`${API_V1_URL}${path}`, { cache: "no-store", signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) });
    return response.ok ? await response.json() : null;
  } catch {
    return null;
  }
}

/** 공개 조회를 ttlMs 동안 보관한다. 같은 조회가 동시에 여러 번 와도 백엔드는 한 번만 부른다. 실패하면 예전 값, 없으면 null. */
async function cachedJson<T>(path: string, ttlMs: number): Promise<T | null> {
  const now = Date.now();
  const entry = entries.get(path);
  const usable = entry && entry.data !== null && now - entry.at < MAX_STALE_MS ? (entry.data as T) : null;
  if (entry && entry.data !== null && now - entry.at < ttlMs) return entry.data as T;
  if (entry && now - entry.failedAt < RETRY_AFTER_FAILURE_MS) return usable;

  let pending = inflight.get(path);
  if (!pending) {
    pending = requestJson(path).then((data) => {
      const at = Date.now();
      if (data !== null) entries.set(path, { at, data, failedAt: 0 });
      else entries.set(path, { at: entry?.at ?? 0, data: entry?.data ?? null, failedAt: at });
      inflight.delete(path);
      return data;
    });
    inflight.set(path, pending);
  }
  return ((await pending) as T | null) ?? usable;
}

export type ResortWeather = {
  temperature: number;
  sky: string | null;
  observedAt: string;
  source: "OFFICIAL" | "KMA";
  stale: boolean;
};

export type SlopeSpecSummary = {
  code: string;
  slopeCount: number;
  longestLengthM: number | null;
  steepestMaxGradient: number | null;
};

export type ReportRow = { reportId: number; resortCode: string; authorNickname: string; content: string; createdAt: string };

export type CarpoolRow = {
  publicId: string;
  title: string;
  departureRegion: string;
  destinationResortName: string;
  departureAt: string;
};

const MINUTE = 60 * 1000;
// 목록 조회는 한 번에 받는 개수를 정해 둔다. 리조트마다 따로 부르지 않고 한 번 받은 목록을 리조트별로 나눠 쓴다.
const REPORT_LIST_SIZE = 100;
const CARPOOL_LIST_SIZE = 100;

export const getOpenings = () => cachedJson<SeasonOpenings>("/public/season-openings", 5 * MINUTE);

export const getWeather = (resortCode: string) =>
  cachedJson<ResortWeather>(`/resort-cams/${encodeURIComponent(resortCode)}/weather`, 2 * MINUTE);

export async function getSlopeSpecs(): Promise<SlopeSpecSummary[] | null> {
  const list = await cachedJson<{ resorts?: SlopeSpecSummary[] }>("/slopes/resorts", 30 * MINUTE);
  return Array.isArray(list?.resorts) ? list.resorts : null;
}

/** 오늘 설질 제보(최신순). 실패하면 null. */
export async function getTodayReports(): Promise<ReportRow[] | null> {
  const page = await cachedJson<{ content?: ReportRow[] }>(`/resort-reports/today?page=1&size=${REPORT_LIST_SIZE}`, MINUTE);
  return Array.isArray(page?.content) ? page.content : null;
}

/** 출발 전인 카풀 모집글. 검색어 조회는 IP별 횟수 제한이 있어 쓰지 않고, 전체 목록을 받아 리조트 이름으로 나눈다. 실패하면 null. */
export async function getOpenCarpools(): Promise<CarpoolRow[] | null> {
  const page = await cachedJson<{ content?: CarpoolRow[] }>(`/carpools?page=0&size=${CARPOOL_LIST_SIZE}`, 2 * MINUTE);
  return Array.isArray(page?.content) ? page.content : null;
}

const RESORT_SLUG = /^[a-z0-9-]{1,40}$/;

/** 리조트 목록은 슬로프캠과 같은 목록을 쓴다. 주소도 슬로프캠, 슬로프 제원과 같은 표기(konjiam, welli-hilli)다. */
export async function findResort(slug: string): Promise<{ resort: ResortCameraGroup | null; resorts: ResortCameraGroup[] }> {
  const resorts = await getResortCameras();
  const resort = RESORT_SLUG.test(slug) ? resorts.find((item) => resortSlug(item.code) === slug) : undefined;
  return { resort: resort ?? null, resorts };
}
