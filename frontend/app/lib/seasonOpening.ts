// 26/27 시즌 리조트별 개장일. 공개 API가 없어 직접 넣는다(docs/conception/019_resort_weather_dday).
// 공식 발표 전 날짜는 작년 개장일과 같은 주의 금요일로 잡은 예상일이다. 확정되면 confirmed를 true로 바꾼다.
// 날짜가 없는 리조트는 D-DAY를 표시하지 않는다.
export type SeasonOpening = {
  resortCode: string;
  name: string;
  date: string;
  confirmed: boolean;
};

export const SEASON_OPENINGS: SeasonOpening[] = [
  { resortCode: "PHOENIX", name: "휘닉스파크", date: "2026-11-20", confirmed: true },
  { resortCode: "YONGPYONG", name: "모나용평", date: "2026-11-20", confirmed: true },
  { resortCode: "HIGH1", name: "하이원", date: "2026-11-27", confirmed: true },
  { resortCode: "O2_RESORT", name: "오투리조트", date: "2026-11-27", confirmed: false },
  { resortCode: "VIVALDI", name: "비발디파크", date: "2026-12-04", confirmed: false },
  { resortCode: "WELLI_HILLI", name: "웰리힐리파크", date: "2026-12-04", confirmed: false },
  { resortCode: "MUJU", name: "무주덕유산", date: "2026-12-04", confirmed: false },
  { resortCode: "WELLI_HILLI", name: "웰리힐리파크", date: "2026-12-04", confirmed: false },
  { resortCode: "OAK_VALLEY", name: "오크밸리", date: "2026-12-11", confirmed: false },
  { resortCode: "KONJIAM", name: "곤지암", date: "2026-12-11", confirmed: false },
];

const DAY_MILLIS = 24 * 60 * 60 * 1000;

/** KST 기준 오늘 날짜(YYYY-MM-DD). 브라우저 시간대와 무관하다. */
export function kstDateKey(now: Date): string {
  // en-CA는 YYYY-MM-DD 형식으로 나온다.
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(now);
}

function dateKeyToUtc(dateKey: string): number {
  const [year, month, day] = dateKey.split("-").map(Number);
  return Date.UTC(year, month - 1, day);
}

/** KST 달력 날짜 기준 남은 일수. 0이면 개장 당일, 음수면 이미 개장. today는 {@link kstDateKey} 값. */
export function daysUntil(date: string, today: string): number {
  return Math.round((dateKeyToUtc(date) - dateKeyToUtc(today)) / DAY_MILLIS);
}

export function formatDday(days: number): string {
  return days === 0 ? "D-DAY" : `D-${days}`;
}

export type UpcomingOpening = { opening: SeasonOpening; days: number };

/** 아직 개장하지 않은 리조트의 D-DAY. 개장일이 지났거나 날짜가 없으면 null. */
export function upcomingOpening(resortCode: string, today: string): UpcomingOpening | null {
  const opening = SEASON_OPENINGS.find((item) => item.resortCode === resortCode);
  if (!opening) return null;
  const days = daysUntil(opening.date, today);
  return days >= 0 ? { opening, days } : null;
}

/** 개장일이 확정됐고 아직 개장 전인 리조트 전부, 개장일 순. 메인 배너에는 예상일을 걸지 않는다. */
export function confirmedUpcomingOpenings(today: string): UpcomingOpening[] {
  return SEASON_OPENINGS.filter((opening) => opening.confirmed)
    .map((opening) => ({ opening, days: daysUntil(opening.date, today) }))
    .filter((item) => item.days >= 0)
    .sort((a, b) => a.days - b.days || a.opening.name.localeCompare(b.opening.name, "ko"));
}
