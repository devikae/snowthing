"use client";

import Link from "next/link";
import { formatDday, nextOpenings } from "../lib/seasonOpening";
import { useKstToday } from "../lib/useKstToday";

function formatMonthDay(date: string): string {
  const [, month, day] = date.split("-");
  return `${Number(month)}.${Number(day)}`;
}

/** 메인 첫 개장 D-DAY 배너. 시즌 시작 전 잠깐 쓰고, 모든 리조트가 개장하면 그리지 않는다. */
export function OpeningDdayBanner() {
  const today = useKstToday();
  const next = today ? nextOpenings(today) : null;

  if (!next) return null;
  const [first] = next.openings;
  const names = next.openings.map((opening) => opening.name).join("·");
  const expected = next.openings.some((opening) => !opening.confirmed);

  return (
    <Link href="/resort-cam" className="opening-dday-banner">
      <span className="material-symbols-outlined" aria-hidden="true">ac_unit</span>
      <strong>
        첫 개장까지 <em>{formatDday(next.days)}</em>
      </strong>
      <span className="opening-dday-detail">
        {names} {formatMonthDay(first.date)}
        {expected ? " 예상" : ""}
      </span>
      <span className="opening-dday-more">
        리조트별 보기
        <span className="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
      </span>
    </Link>
  );
}
