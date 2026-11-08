export const KOREAN_DATE_TIME: Intl.DateTimeFormatOptions = { dateStyle: "medium", timeStyle: "short" };

const DATE_ONLY: Intl.DateTimeFormatOptions = { year: "numeric", month: "numeric", day: "numeric" };

// 서버 시각은 오프셋 없는 KST 문자열이라 브라우저 시간대와 상관없이 KST로 읽는다.
function toDate(value: string | Date) {
  if (value instanceof Date) return value;
  return new Date(/([zZ]|[+-]\d{2}:?\d{2})$/.test(value) ? value : `${value}+09:00`);
}

/** 한국어 날짜를 KST로 보여 준다. 브라우저 기본 형식에서 일 뒤의 점만 뺀다. 예: 2026. 11. 6 오전 3:36 */
export function formatKoreanDate(value: string | Date, options: Intl.DateTimeFormatOptions = DATE_ONLY) {
  const date = toDate(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", ...options })
    .format(date)
    .replace(/^(\d{4}\. \d{1,2}\. \d{1,2})\./, "$1");
}

const KST_PARTS = new Intl.DateTimeFormat("en-US", { timeZone: "Asia/Seoul", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" });

function kstParts(date: Date) {
  const parts = Object.fromEntries(KST_PARTS.formatToParts(date).map((part) => [part.type, part.value]));
  return { month: parts.month, day: parts.day, hour: parts.hour, minute: parts.minute };
}

/** KST 기준 "MM.DD HH:mm". 댓글과 카풀 출발 시각 등에 쓴다. */
export function formatShortDateTime(value: string | Date) {
  const date = toDate(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const { month, day, hour, minute } = kstParts(date);
  return `${month}.${day} ${hour}:${minute}`;
}

/** 목록용 상대 시각. 1분 안 "방금", 1시간 안 "N분", 하루 안 "N시간", 하루 지나면 "어제", 그 뒤는 KST "MM.DD". */
export function formatCompactTime(value: string | Date, now: Date = new Date()) {
  const date = toDate(value);
  if (Number.isNaN(date.getTime())) return "";
  const diffSec = Math.floor((now.getTime() - date.getTime()) / 1000);
  if (diffSec < 60) return "방금";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}분`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간`;
  if (Math.floor(diffHour / 24) === 1) return "어제";
  const { month, day } = kstParts(date);
  return `${month}.${day}`;
}
