export interface ResortCount {
  resortId: number;
  resortName: string;
  days: number;
}

export interface SeasonVisit {
  visitDate: string;
  resortId: number;
  resortName: string;
}

export interface SeasonLog {
  season: { startYear: number; label: string; startDate: string; endDate: string };
  latestSeasonStartYear: number;
  today: string;
  recordableFrom: string;
  visits: SeasonVisit[];
  summary: { totalDays: number; firstDate: string | null; lastDate: string | null; topResort: ResortCount | null; resorts: ResortCount[] };
  shareToken: string | null;
}

// 공개 결산. 날짜와 닉네임 외 회원 정보는 백엔드가 보내지 않는다.
export interface SharedSeason {
  nickname: string;
  seasonStartYear: number;
  seasonLabel: string;
  totalDays: number;
  topResort: ResortCount | null;
  resorts: ResortCount[];
}

export function shareHeadline(nickname: string, totalDays: number) {
  return totalDays > 0 ? `${nickname} 님은 ${totalDays}일 탔어요` : `${nickname} 님은 아직 기록이 없어요`;
}

// 카드에는 큰 숫자로 일수를 따로 보여 주므로 제목에서 일수를 뺀다.
export function cardHeadline(nickname: string) {
  return `${nickname} 님이 탄 날`;
}

export function shareUrl(origin: string, shareToken: string) {
  return new URL(`/season-log/s/${encodeURIComponent(shareToken)}`, origin).toString();
}

export function topResortLine(top: ResortCount | null) {
  return top ? `가장 많이 간 곳 ${top.resortName} ${top.days}일` : "아직 다녀온 리조트가 없어요";
}
