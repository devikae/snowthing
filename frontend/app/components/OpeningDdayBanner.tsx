"use client";

import Link from "next/link";
import { confirmedUpcomingOpenings, formatDday } from "../lib/seasonOpening";
import { useKstToday } from "../lib/useKstToday";

function formatMonthDay(date: string): string {
  const [, month, day] = date.split("-");
  return `${Number(month)}.${Number(day)}`;
}

/** 메인 개장 D-DAY 배너. 개장일이 확정된 리조트를 모두 나열하고, 모두 개장하면 그리지 않는다. */
export function OpeningDdayBanner() {
  const today = useKstToday();
  const openings = today ? confirmedUpcomingOpenings(today) : [];

  if (openings.length === 0) return null;

  return (
    <Link href="/resort-cam" className="opening-dday-banner">
      <strong>개장까지</strong>
      <ul className="opening-dday-list">
        {openings.map(({ opening, days }) => (
          <li key={opening.resortCode}>
            <span>{opening.name}</span>
            <em>{formatDday(days)}</em>
            <small>{formatMonthDay(opening.date)}</small>
          </li>
        ))}
      </ul>
      <span className="opening-dday-more">
        리조트별 보기
        <span className="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
      </span>
    </Link>
  );
}
