export function formatKoreanCalendarDate(date: Date): string {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "numeric",
    day: "numeric",
  }).format(date);
}

export function isResortReportSeason(date: Date): boolean {
  const month = Number(
    new Intl.DateTimeFormat("en-US", { timeZone: "Asia/Seoul", month: "numeric" }).format(date),
  );
  return month >= 10 || month <= 4;
}

const RESORT_REPORT_DISPLAY_NAMES: Record<string, string> = {
  "휘닉스파크": "휘닉스",
  "비발디파크": "비발디",
  "하이원리조트": "하이원",
  "모나용평": "모나용평",
  "웰리힐리파크": "웰리힐리",
  "지산리조트": "지산",
  "곤지암리조트": "곤지암",
  "무주덕유산리조트": "무주",
  "에덴밸리리조트": "에덴밸리",
  "엘리시안 강촌": "엘리시안",
  "알펜시아리조트": "알펜시아",
  "오크밸리": "오크밸리",
  "오투리조트": "오투",
};

export function getResortReportDisplayName(name: string): string {
  return RESORT_REPORT_DISPLAY_NAMES[name] ?? name;
}

export function formatReportTime(dateString: string): string {
  try {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat("ko-KR", {
      timeZone: "Asia/Seoul",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(date);
  } catch {
    return "";
  }
}

