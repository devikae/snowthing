export const KOREAN_DATE_TIME: Intl.DateTimeFormatOptions = { dateStyle: "medium", timeStyle: "short" };

const DATE_ONLY: Intl.DateTimeFormatOptions = { year: "numeric", month: "numeric", day: "numeric" };

// 서버 시각은 오프셋 없는 KST 문자열이라 브라우저 시간대와 상관없이 KST로 읽는다.
export function toDate(value: string | Date) {
  if (value instanceof Date) return value;
  return new Date(/([zZ]|[+-]\d{2}:?\d{2})$/.test(value) ? value : `${value}+09:00`);
}

// ko-KR 형식은 실행 환경의 ICU 데이터에 따라 달라진다(운영 Node alpine은 "오전" 대신 "AM").
// 서버 렌더링과 브라우저 결과가 다르면 하이드레이션 오류가 나므로, 숫자만 받아 한국어 문구는 직접 붙인다.
const KST_NUMERIC = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "numeric",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
  hourCycle: "h23",
});

const pad2 = (value: string) => value.padStart(2, "0");

/** 한국어 날짜를 KST로 보여 준다. 예: 2026. 11. 6 오전 3:36. 서버와 브라우저에서 같은 글자가 나온다. */
export function formatKoreanDate(value: string | Date, options: Intl.DateTimeFormatOptions = DATE_ONLY) {
  const date = toDate(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const p = Object.fromEntries(KST_NUMERIC.formatToParts(date).map((part) => [part.type, part.value]));
  const pieces: string[] = [];

  if (options.dateStyle || options.year || options.month || options.day) {
    if (options.month === "long") {
      pieces.push(`${options.year ? `${p.year}년 ` : ""}${p.month}월 ${p.day}일`);
    } else {
      const month = options.month === "2-digit" ? pad2(p.month) : p.month;
      const day = options.day === "2-digit" ? pad2(p.day) : p.day;
      pieces.push(options.dateStyle || options.year ? `${p.year}. ${month}. ${day}` : `${month}. ${day}`);
    }
  }

  if (options.timeStyle || options.hour || options.minute) {
    const hour = Number(p.hour) % 24;
    if (options.hourCycle === "h23" || options.hour12 === false) {
      pieces.push(`${options.hour === "2-digit" ? pad2(String(hour)) : hour}:${p.minute}`);
    } else {
      const hour12 = String(hour % 12 || 12);
      pieces.push(`${hour < 12 ? "오전" : "오후"} ${options.hour === "2-digit" ? pad2(hour12) : hour12}:${p.minute}`);
    }
  }

  return pieces.join(" ");
}

const KST_PARTS = new Intl.DateTimeFormat("en-US", { timeZone: "Asia/Seoul", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" });

function kstParts(date: Date) {
  const parts = Object.fromEntries(KST_PARTS.formatToParts(date).map((part) => [part.type, part.value]));
  return { month: parts.month, day: parts.day, hour: parts.hour, minute: parts.minute };
}

/** KST 기준 "HH:mm"(00~23시). 설질 제보 시각에 쓴다. */
export function formatKstTime(value: string | Date) {
  const date = toDate(value);
  if (Number.isNaN(date.getTime())) return "";
  const { hour, minute } = kstParts(date);
  return `${hour}:${minute}`;
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
