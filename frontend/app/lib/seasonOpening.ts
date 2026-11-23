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
  /** 배너 맨 앞 문구. 관리자가 정하고, 없으면 기본 문구를 쓴다. */
  bannerTitle?: string;
  /** 켜면 개장한 리조트도 배너에 "개장 D+N"으로 남긴다. 예전 서버는 이 값을 주지 않으므로 없으면 끔. */
  showAfterOpening?: boolean;
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

/** 남은 일수를 D-DAY 표기로. 개장일이 지났으면(음수) "개장 D+N"이다. */
export function formatDday(days: number): string {
  if (days === 0) return "D-DAY";
  return days > 0 ? `D-${days}` : `개장 D+${-days}`;
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

export const DEFAULT_BANNER_TITLE = "개장까지";
/** 개장한 리조트가 섞여 있는데 문구가 기본값이면 "개장까지"가 어색해서 대신 쓰는 문구. */
export const OPENED_BANNER_TITLE = "개장 소식";
export const BANNER_TITLE_MAX = 12;

/**
 * 메인 배너에 걸 리조트. 스위치가 꺼졌으면 빈 목록, 켜졌으면 배너에 넣기로 한 리조트를 관리자가 정한 순서대로.
 * 개장한 리조트는 showAfterOpening이 켜져 있을 때만 남는다(days가 음수).
 */
export function bannerOpenings(data: SeasonOpenings, today: string): UpcomingOpening[] {
  if (!data.bannerEnabled) return [];
  return data.resorts
    .filter((opening) => opening.showOnBanner && opening.openingDate)
    .map((opening) => ({ opening, date: opening.openingDate as string, days: daysUntil(opening.openingDate as string, today) }))
    .filter((item) => item.days >= 0 || data.showAfterOpening === true);
}

/** 배너 맨 앞 문구. 관리자가 정한 문구를 쓰되, 기본 문구인데 개장한 리조트가 있으면 "개장 소식"으로 바꾼다. */
export function bannerTitle(data: SeasonOpenings, items: UpcomingOpening[]): string {
  const title = data.bannerTitle?.trim() || DEFAULT_BANNER_TITLE;
  return title === DEFAULT_BANNER_TITLE && items.some((item) => item.days < 0) ? OPENED_BANNER_TITLE : title;
}
