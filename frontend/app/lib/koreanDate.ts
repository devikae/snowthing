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
