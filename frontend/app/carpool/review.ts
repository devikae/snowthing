// 카풀 후기 공통 값. 설계: docs/conception/027_carpool_review/01_design.md

export type CarpoolReviewTag = "ON_TIME" | "KIND" | "FAIR_SETTLEMENT" | "SAFE_DRIVING" | "CLEAN_CAR";
export type CarpoolReviewRating = "GOOD" | "BAD";

export const CARPOOL_REVIEW_TAGS: { tag: CarpoolReviewTag; label: string; driverOnly: boolean }[] = [
  { tag: "ON_TIME", label: "시간 약속을 잘 지켜요", driverOnly: false },
  { tag: "KIND", label: "친절해요", driverOnly: false },
  { tag: "FAIR_SETTLEMENT", label: "정산이 깔끔해요", driverOnly: false },
  { tag: "SAFE_DRIVING", label: "안전하게 운전해요", driverOnly: true },
  { tag: "CLEAN_CAR", label: "차가 깨끗해요", driverOnly: true },
];

export const carpoolReviewTagLabel = (tag: CarpoolReviewTag) =>
  CARPOOL_REVIEW_TAGS.find((item) => item.tag === tag)?.label ?? tag;

export interface CarpoolReviewSummary {
  rideCount: number;
  goodCount: number;
  topTags: { tag: CarpoolReviewTag; count: number }[];
}

/** 공개 요약 한 줄. 기록이 없으면 빈 문자열이라 화면에서 숨긴다. 아쉬웠어요 개수는 보여 주지 않는다. */
export function carpoolSummaryText(summary: CarpoolReviewSummary | null): string {
  if (!summary || (summary.rideCount === 0 && summary.goodCount === 0)) return "";
  const parts = [`카풀 ${summary.rideCount}회`];
  if (summary.goodCount > 0) parts.push(`좋았어요 ${summary.goodCount}`);
  if (summary.topTags[0]) parts.push(carpoolReviewTagLabel(summary.topTags[0].tag));
  return parts.join(", ");
}

export async function responseMessage(response: Response, fallback: string) {
  const data = await response.json().catch(() => null);
  return typeof data?.message === "string" ? data.message : fallback;
}
