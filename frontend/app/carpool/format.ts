import { toDate } from "../lib/koreanDate";

const DAY_NAMES = ["일", "월", "화", "수", "목", "금", "토"];
const WEEKDAY_INDEX: Record<string, number> = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
const KST_PARTS = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Seoul",
  month: "numeric",
  day: "numeric",
  weekday: "short",
  hour: "numeric",
  minute: "2-digit",
  hourCycle: "h23",
});

// 출발 시각은 브라우저 시간대와 상관없이 KST로 보여 준다.
export function formatCarpoolDateTime(isoString: string): string {
  const date = toDate(isoString);
  if (isNaN(date.getTime())) return isoString;
  const parts = Object.fromEntries(KST_PARTS.formatToParts(date).map((part) => [part.type, part.value]));
  const hour = Number(parts.hour);
  return `${parts.month}.${parts.day} (${DAY_NAMES[WEEKDAY_INDEX[parts.weekday]]}) ${
    hour < 12 ? "오전" : "오후"
  } ${hour % 12 || 12}:${parts.minute}`;
}

export function carpoolCostLabel(costMode: "AUTO" | "MANUAL"): string {
  return costMode === "MANUAL" ? "1인 비용" : "예상 1인 비용";
}
