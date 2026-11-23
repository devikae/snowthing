"use client";

import Link from "next/link";
import { DEFAULT_BANNER_TITLE, bannerOpenings, formatDday } from "../lib/seasonOpening";
import { useKstToday } from "../lib/useKstToday";
import { useSeasonOpenings } from "../lib/useSeasonOpenings";

function formatMonthDay(date: string): string {
  const [, month, day] = date.split("-");
  return `${Number(month)}.${Number(day)}`;
}

/**
 * 메인 개장 D-DAY 배너. 관리자가 배너에 넣기로 한 리조트를 정한 순서대로 나열한다.
 * 스위치가 꺼졌거나, 넣은 리조트가 모두 개장했거나, 개장일을 받지 못하면 그리지 않는다.
 */
export function OpeningDdayBanner() {
  const today = useKstToday();
  const data = useSeasonOpenings();
  const openings = today && data ? bannerOpenings(data, today) : [];

  if (openings.length === 0) return null;

  return (
    <Link href="/resorts" className="opening-dday-banner">
      <strong>{data?.bannerTitle?.trim() || DEFAULT_BANNER_TITLE}</strong>
      <ul className="opening-dday-list">
        {openings.map(({ opening, date, days }) => (
          <li key={opening.resortCode}>
            <span>{opening.name}</span>
            <em>{formatDday(days)}</em>
            <small>{formatMonthDay(date)}</small>
          </li>
        ))}
      </ul>
      <span className="opening-dday-more">
        리조트별 보기
        <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
      </span>
    </Link>
  );
}
