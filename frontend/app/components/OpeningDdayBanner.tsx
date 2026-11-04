"use client";

import Link from "next/link";
import { formatDday, nextConfirmedOpenings } from "../lib/seasonOpening";
import { useKstToday } from "../lib/useKstToday";

function formatMonthDay(date: string): string {
  const [, month, day] = date.split("-");
  return `${Number(month)}.${Number(day)}`;
}

/** 메인 개장 D-DAY 배너. 확정된 개장일만 걸고, 확정된 리조트가 모두 개장하면 그리지 않는다. */
export function OpeningDdayBanner() {
  const today = useKstToday();
  const next = today ? nextConfirmedOpenings(today) : null;

  if (!next) return null;
  const [first] = next.openings;
  const names = next.openings.map((opening) => opening.name).join(", ");

  return (
    <Link href="/resort-cam" className="opening-dday-banner">
      <span className="material-symbols-outlined" aria-hidden="true">ac_unit</span>
      <strong>
        개장까지 <em>{formatDday(next.days)}</em>
      </strong>
      <span className="opening-dday-detail">
        {names} {formatMonthDay(first.date)}
      </span>
      <span className="opening-dday-more">
        리조트별 보기
        <span className="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
      </span>
    </Link>
  );
}
