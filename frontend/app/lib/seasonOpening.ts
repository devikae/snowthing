// 리조트별 개장일 D-DAY 계산. 개장일 값은 관리자 화면(/admin/season-openings)에서 바꾸고
// 공개 API(/api/v1/public/season-openings)로 받는다(IKA-87, docs/conception/032_season_opening_admin).
// 날짜가 없는 리조트는 D-DAY를 표시하지 않는다.
export type SeasonOpening = {
  resortCode: string;
  name: string;
  /** YYYY-MM-DD, KST 달력 날짜. 없으면 null. */
  openingDate: string | null;
  /** 리조트가 공식 발표한 날짜인지. 아니면 화면에 "예상"을 붙인다. */
  confirmed: boolean;
  showOnBanner: boolean;
  bannerOrder: number;
};

export type SeasonOpenings = {
  bannerEnabled: boolean;
  /** 배너 순서, 같으면 등록 순. */
  resorts: SeasonOpening[];
};

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

export type UpcomingOpening = { opening: SeasonOpening; date: string; days: number };

function toUpcoming(opening: SeasonOpening, today: string): UpcomingOpening | null {
  if (!opening.openingDate) return null;
  const days = daysUntil(opening.openingDate, today);
  return days >= 0 ? { opening, date: opening.openingDate, days } : null;
}

/** 아직 개장하지 않은 리조트의 D-DAY. 개장일이 지났거나 날짜가 없으면 null. */
export function upcomingOpening(
  openings: SeasonOpening[],
  resortCode: string,
  today: string,
): UpcomingOpening | null {
  const opening = openings.find((item) => item.resortCode === resortCode);
  return opening ? toUpcoming(opening, today) : null;
}

/** 메인 배너에 걸 리조트. 스위치가 꺼졌으면 빈 목록, 켜졌으면 배너에 넣기로 한 리조트 중 아직 개장 전인 것을 관리자가 정한 순서대로. */
export function bannerOpenings(data: SeasonOpenings, today: string): UpcomingOpening[] {
  if (!data.bannerEnabled) return [];
  return data.resorts
    .filter((opening) => opening.showOnBanner)
    .map((opening) => toUpcoming(opening, today))
    .filter((item): item is UpcomingOpening => item !== null);
}
