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

/** 리조트 이름 옆 개장 D-DAY 배지. 확정일은 D-n만, 예상일은 "예상"을 붙인다. 브라우저에서만 계산한다. */
export function OpeningDdayTag({ resortCode }: { resortCode: string }) {
  const today = useKstToday();
  const upcoming = today ? upcomingOpening(resortCode, today) : null;

  if (!upcoming) return null;
  return (
    <span className={styles.ddayBadge} title={`개장일 ${upcoming.opening.date}`}>
      {formatDday(upcoming.days)}
      {upcoming.opening.confirmed ? null : <small>예상</small>}
    </span>
  );
}

const NIGHT_HOURS = { from: 18, until: 6 };

const KST_HOUR = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Seoul",
  hour: "numeric",
  hourCycle: "h23",
});

type SkyIcon = { name: string; tone: "sun" | "night" | "cloud" | "rain" | "snow" | "fog" };

/** 하늘 상태 문구를 Material Symbols 아이콘으로. 모르는 문구나 하늘 정보가 없으면 아이콘을 그리지 않는다. */
function skyIcon(sky: string | null, observedAt: string): SkyIcon | null {
  if (!sky) return null;
  const hour = Number(KST_HOUR.format(new Date(observedAt)));
  const night = hour >= NIGHT_HOURS.from || hour < NIGHT_HOURS.until;
  if (sky.includes("비") && sky.includes("눈")) return { name: "weather_mix", tone: "rain" };
  if (sky.includes("눈")) return { name: "weather_snowy", tone: "snow" };
  if (sky.includes("비") || sky.includes("빗방울") || sky.includes("소나기")) return { name: "rainy", tone: "rain" };
  if (sky.includes("안개")) return { name: "foggy", tone: "fog" };
  if (sky.includes("구름 조금") || sky.includes("구름조금")) {
    return night ? { name: "partly_cloudy_night", tone: "night" } : { name: "partly_cloudy_day", tone: "cloud" };
  }
  if (sky.includes("구름") || sky.includes("흐림")) return { name: "cloud", tone: "cloud" };
  if (sky.includes("맑음")) return night ? { name: "clear_night", tone: "night" } : { name: "sunny", tone: "sun" };
  return null;
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
    .join(" / ");
  const icon = skyIcon(weather.sky, weather.observedAt);
  return (
    <div
      className={styles.weatherChip}
      title={weather.stale ? "최근 갱신에 실패해 이전 값을 보여 줍니다." : undefined}
    >
      {icon && (
        <span className={`material-symbols-outlined ${styles.skyIcon}`} data-tone={icon.tone} aria-hidden="true">
          {icon.name}
        </span>
      )}
      <div>
        <strong>{weather.temperature.toFixed(1)}°C</strong>
        <small>{details}</small>
      </div>
    </div>
  );
}
