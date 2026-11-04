"use client";

import { useEffect, useState } from "react";
import { API_ENDPOINTS } from "../lib/api";
import { formatDday, upcomingOpening } from "../lib/seasonOpening";
import { useKstToday } from "../lib/useKstToday";
import styles from "./resortCam.module.css";

type ResortWeather = {
  temperature: number;
  sky: string | null;
  observedAt: string;
  source: "OFFICIAL" | "KMA";
  stale: boolean;
};

const SOURCE_LABELS: Record<ResortWeather["source"], string> = {
  OFFICIAL: "공식",
  KMA: "기상청",
};

const KST_TIME = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

/** 개장 전 리조트의 D-DAY 태그. 브라우저에서만 계산한다. */
export function OpeningDdayTag({ resortCode }: { resortCode: string }) {
  const today = useKstToday();
  const upcoming = today ? upcomingOpening(resortCode, today) : null;

  if (!upcoming) return null;
  return (
    <span className={styles.ddayMeta}>
      개장 {formatDday(upcoming.days)}
      {upcoming.opening.confirmed ? "" : " (예상)"}
    </span>
  );
}

/** 리조트 기온 칩. 페이지 렌더와 분리해 받고, 값이 없거나 실패하면 아무것도 그리지 않는다. */
export function ResortWeatherChip({ resortCode }: { resortCode: string }) {
  const [state, setState] = useState<{ code: string; weather: ResortWeather | null } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetch(API_ENDPOINTS.resortCams.weather(resortCode), { signal: controller.signal })
      .then(async (response) => {
        const weather = response.status === 200 ? ((await response.json()) as ResortWeather) : null;
        setState({ code: resortCode, weather });
      })
      .catch(() => {
        if (!controller.signal.aborted) setState({ code: resortCode, weather: null });
      });
    return () => controller.abort();
  }, [resortCode]);

  if (!state || state.code !== resortCode) {
    return <div className={styles.weatherChipPlaceholder} aria-hidden="true" />;
  }
  const { weather } = state;
  if (!weather) return null;

  const details = [weather.sky, `${KST_TIME.format(new Date(weather.observedAt))} 기준`, SOURCE_LABELS[weather.source]]
    .filter(Boolean)
    .join(" · ");
  return (
    <div
      className={styles.weatherChip}
      title={weather.stale ? "최근 갱신에 실패해 이전 값을 보여 줍니다." : undefined}
    >
      <span className="material-symbols-outlined" aria-hidden="true">device_thermostat</span>
      <div>
        <strong>{weather.temperature.toFixed(1)}°C</strong>
        <small>{details}</small>
      </div>
    </div>
  );
}
