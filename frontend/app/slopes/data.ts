import { cache } from "react";
import { notFound } from "next/navigation";
import { fetchPublicResult } from "../lib/publicApi";
import { codeFromSlug, type ResortDetail, type ResortSummary, type SlopeDetail } from "./specs";

const RESORT_SLUG = /^[a-z0-9-]{1,40}$/;

// 서버 컴포넌트에서 쓴다. 같은 요청 안의 metadata와 본문이 한 번만 부르도록 cache로 감싼다.
export const getSlopeResorts = cache(async () => {
  const result = await fetchPublicResult<{ resorts: ResortSummary[] }>("/slopes/resorts");
  return result.data?.resorts ?? null;
});

export const getResortDetail = cache(async (slug: string) => {
  if (!RESORT_SLUG.test(slug)) return { data: null, missing: true };
  return fetchPublicResult<ResortDetail>(`/slopes/resorts/${codeFromSlug(slug)}`);
});

export const getSlopeDetail = cache(async (slopeId: string) => {
  if (!/^\d{1,12}$/.test(slopeId)) return { data: null, missing: true };
  return fetchPublicResult<SlopeDetail>(`/slopes/${slopeId}`);
});

// 404면 notFound, 그 밖의 실패(시간 초과 등)는 오류 화면을 보여 주도록 던진다.
export async function requireResortDetail(slug: string) {
  const result = await getResortDetail(slug);
  if (result.missing) notFound();
  if (!result.data) throw new Error("슬로프 제원을 불러오지 못했습니다.");
  return result.data;
}

export async function requireSlopeDetail(slug: string, slopeId: string) {
  const result = await getSlopeDetail(slopeId);
  if (result.missing) notFound();
  if (!result.data) throw new Error("슬로프 제원을 불러오지 못했습니다.");
  if (codeFromSlug(slug) !== result.data.resort.code) notFound();
  return result.data;
}
