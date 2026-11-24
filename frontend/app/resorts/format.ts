import { daysUntil, formatDday, type SeasonOpening } from "../lib/seasonOpening";
import { RESORT_PASS_PRICES, type ResortPassPrice } from "../lib/seasonPassPrices";
import { resortSlug } from "../resort-cam/types";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** YYYY-MM-DD(KST 달력 날짜)를 "2026년 12월 11일 (금)"로. */
export function formatOpeningDate(dateKey: string): string {
  const [year, month, day] = dateKey.split("-").map(Number);
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
  return `${year}년 ${month}월 ${day}일 (${weekday})`;
}

export function formatMonthDay(dateKey: string): string {
  const [, month, day] = dateKey.split("-").map(Number);
  return `${month}월 ${day}일`;
}

export type OpeningView =
  | { kind: "none" }
  /** 개장 전. 확정이 아니면 기존 화면처럼 "예상"을 붙인다. */
  | { kind: "upcoming"; date: string; dday: string; confirmed: boolean }
  /** 확정된 개장일이 지났다. */
  | { kind: "opened"; date: string }
  /** 예상일이 지났는데 확정 발표가 등록되지 않았다. 개장했다고 말하지 않는다. */
  | { kind: "passedEstimate"; date: string };

export function openingView(opening: SeasonOpening | undefined, today: string): OpeningView {
  if (!opening?.openingDate) return { kind: "none" };
  const days = daysUntil(opening.openingDate, today);
  if (days >= 0) return { kind: "upcoming", date: opening.openingDate, dday: formatDday(days), confirmed: opening.confirmed };
  return opening.confirmed ? { kind: "opened", date: opening.openingDate } : { kind: "passedEstimate", date: opening.openingDate };
}

/** 목록과 검색 설명에 쓰는 한 줄. 날짜가 없으면 null. */
export function openingSummary(view: OpeningView): string | null {
  switch (view.kind) {
    case "upcoming":
      return `${formatMonthDay(view.date)} 개장 ${view.confirmed ? "확정" : "예상"}`;
    case "opened":
      return `${formatMonthDay(view.date)} 개장`;
    case "passedEstimate":
      return `개장 예상일 ${formatMonthDay(view.date)}`;
    default:
      return null;
  }
}

// 시즌권 가격 데이터는 리조트 코드 표기가 조금 다르다(WELLIHILLI). 밑줄을 빼고 맞춘다.
const plainCode = (code: string) => code.replace(/_/g, "");

export function passPriceFor(resortCode: string): ResortPassPrice | undefined {
  return RESORT_PASS_PRICES.find((item) => plainCode(item.code) === plainCode(resortCode));
}

// 시즌권 가격 표에서 리조트 페이지로 갈 때 쓴다. 가격 데이터의 코드만 표기가 달라 여기서 주소 표기로 바꾼다.
const PASS_CODE_TO_RESORT_CODE: Record<string, string> = { WELLIHILLI: "WELLI_HILLI" };

export function resortPathForPassCode(passCode: string): string {
  return `/resorts/${resortSlug(PASS_CODE_TO_RESORT_CODE[passCode] ?? passCode)}`;
}

export const won = (value: number) => `${value.toLocaleString("ko-KR")}원`;

const KST_TIME = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
export const formatKstTime = (value: string) => KST_TIME.format(new Date(value));
