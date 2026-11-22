"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { API_V1_URL } from "../lib/api";
import { CourseCard } from "./SlopeMapViews";
import styles from "./slopeSpecs.module.css";
import {
  COMPARE_MAX,
  COUNTRY_LABEL,
  comparePath,
  difficultyText,
  formatGradient,
  formatLength,
  slopePath,
  type Country,
  type ResortDetail,
  type ResortSummary,
  type SlopeDetail,
  type SlopeItem,
} from "./specs";

type Loaded = { status: "loading" } | { status: "error" } | { status: "ready"; detail: SlopeDetail };

const slopeCache = new Map<number, Promise<SlopeDetail | null>>();

function loadSlope(id: number) {
  let pending = slopeCache.get(id);
  if (!pending) {
    pending = fetch(`${API_V1_URL}/slopes/${id}`)
      .then((response) => (response.ok ? (response.json() as Promise<SlopeDetail>) : null))
      .catch(() => null);
    slopeCache.set(id, pending);
  }
  return pending;
}

function parseIds(value: string | null) {
  const ids = (value ?? "")
    .split(",")
    .map((part) => Number(part))
    .filter((id) => Number.isInteger(id) && id > 0);
  return Array.from(new Set(ids)).slice(0, COMPARE_MAX);
}

export function CompareView({ resorts }: { resorts: ResortSummary[] }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const ids = useMemo(() => parseIds(searchParams.get("ids")), [searchParams]);
  const idsKey = ids.join(",");
  const [loaded, setLoaded] = useState<Record<number, Loaded>>({});

  useEffect(() => {
    let alive = true;
    for (const id of ids) {
      loadSlope(id).then((detail) => {
        if (!alive) return;
        setLoaded((current) => ({ ...current, [id]: detail ? { status: "ready", detail } : { status: "error" } }));
      });
    }
    return () => {
      alive = false;
    };
    // ids는 idsKey로 바뀜을 판단한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idsKey]);

  const go = (next: number[]) => router.replace(next.length ? comparePath(next) : pathname, { scroll: false });
  const columns = ids.map((id) => ({ id, state: loaded[id] ?? ({ status: "loading" } as Loaded) }));
  const ready = columns.flatMap((column) => (column.state.status === "ready" ? [column.state.detail] : []));

  return (
    <>
      {ids.length < COMPARE_MAX && <SlopePicker resorts={resorts} exclude={ids} onAdd={(id) => go([...ids, id])} />}

      {ids.length < 2 && (
        <p className={styles.notice}>
          비교할 슬로프를 2개 이상 골라 주세요. 국내와 일본 슬로프를 섞어서 최대 {COMPARE_MAX}개까지 고를 수 있습니다.
        </p>
      )}

      {ids.length > 0 && (
        <div className={styles.tableWrap}>
          <table className={styles.compareTable}>
            <thead>
              <tr>
                <th scope="col">슬로프</th>
                {columns.map(({ id, state }) => (
                  <th key={id} scope="col" className={styles.compareHead}>
                    {state.status === "ready" ? (
                      <>
                        <small>
                          {state.detail.resort.name} ({COUNTRY_LABEL[state.detail.resort.country]})
                        </small>
                        <Link href={slopePath(state.detail.resort.code, state.detail.slope.id)}>
                          {state.detail.slope.name}
                        </Link>
                        <div className={styles.compareCourse}>
                          <CourseCard
                            resortCode={state.detail.resort.code}
                            slope={state.detail.slope}
                            slopes={[state.detail.slope]}
                            connections={[]}
                          />
                        </div>
                      </>
                    ) : (
                      <small>{state.status === "loading" ? "불러오는 중" : "슬로프를 찾을 수 없습니다"}</small>
                    )}
                    <button type="button" className={styles.removeButton} onClick={() => go(ids.filter((value) => value !== id))}>
                      빼기
                    </button>
                  </th>
                ))}
              </tr>
            </thead>
            {ready.length > 0 && <CompareRows columns={columns} base={columns[0]?.state} />}
          </table>
          <p className={styles.muted}>
            차이는 첫 번째 슬로프를 기준으로, 두 슬로프가 함께 공개한 값끼리만 보여 줍니다. 코스 카드는 슬로프마다 축척이 다르니 길이를 함께
            보세요.
          </p>
        </div>
      )}
    </>
  );
}

type Column = { id: number; state: Loaded };

function CompareRows({ columns, base }: { columns: Column[]; base: Loaded | undefined }) {
  const baseSlope = base?.status === "ready" ? base.detail.slope : null;
  const cell = (render: (detail: SlopeDetail) => React.ReactNode) =>
    columns.map(({ id, state }) => <td key={id}>{state.status === "ready" ? render(state.detail) : null}</td>);

  const number = (pick: (slope: SlopeItem) => number | null, format: (value: number | null) => string, unit: string) =>
    cell(({ slope }) => {
      const value = pick(slope);
      const baseValue = baseSlope ? pick(baseSlope) : null;
      const showDiff = baseSlope && slope.id !== baseSlope.id && value != null && baseValue != null;
      const diff = showDiff ? Number(value) - Number(baseValue) : 0;
      return (
        <>
          {format(value)}
          {showDiff && (
            <small className={styles.diff} data-sign={diff > 0 ? "up" : diff < 0 ? "down" : "same"}>
              {diff === 0 ? "같음" : `${diff > 0 ? "+" : ""}${unit === "°" ? diff.toFixed(1) : Math.round(diff).toLocaleString("ko-KR")}${unit}`}
            </small>
          )}
        </>
      );
    });

  return (
    <tbody>
      <tr>
        <th scope="row">난이도</th>
        {cell(({ resort, slope }) => (
          <span className={styles.badge} data-difficulty={slope.difficulty}>
            {difficultyText(slope, resort.country)}
          </span>
        ))}
      </tr>
      <tr>
        <th scope="row">길이</th>
        {number((slope) => slope.lengthM, formatLength, "m")}
      </tr>
      <tr>
        <th scope="row">평균 경사</th>
        {number((slope) => slope.avgGradient, formatGradient, "°")}
      </tr>
      <tr>
        <th scope="row">최대 경사</th>
        {number((slope) => slope.maxGradient, formatGradient, "°")}
      </tr>
      <tr>
        <th scope="row">폭</th>
        {number((slope) => slope.widthM, formatLength, "m")}
      </tr>
      <tr>
        <th scope="row">올라가는 리프트</th>
        {cell(({ slope }) => slope.liftName ?? "미공개")}
      </tr>
      <tr>
        <th scope="row">출처</th>
        {cell(({ slope }) => (
          <a href={slope.sourceUrl} target="_blank" rel="noopener noreferrer">
            공식 자료 <small>{slope.checkedAt} 확인</small>
          </a>
        ))}
      </tr>
    </tbody>
  );
}

function SlopePicker({ resorts, exclude, onAdd }: { resorts: ResortSummary[]; exclude: number[]; onAdd: (id: number) => void }) {
  const [resortCode, setResortCode] = useState("");
  const [detail, setDetail] = useState<{ code: string; data: ResortDetail | null } | null>(null);
  const [slopeId, setSlopeId] = useState("");

  useEffect(() => {
    if (!resortCode) return;
    let alive = true;
    fetch(`${API_V1_URL}/slopes/resorts/${resortCode}`)
      .then((response) => (response.ok ? (response.json() as Promise<ResortDetail>) : null))
      .catch(() => null)
      .then((data) => {
        if (alive) setDetail({ code: resortCode, data });
      });
    return () => {
      alive = false;
    };
  }, [resortCode]);

  const slopes = detail && detail.code === resortCode ? (detail.data?.slopes ?? []) : [];
  const countries: Country[] = ["KR", "JP"];

  return (
    <div className={styles.picker}>
      <label>
        리조트
        <select
          value={resortCode}
          onChange={(event) => {
            setResortCode(event.target.value);
            setSlopeId("");
          }}
        >
          <option value="">리조트 고르기</option>
          {countries.map((country) => (
            <optgroup key={country} label={COUNTRY_LABEL[country]}>
              {resorts
                .filter((resort) => resort.country === country)
                .map((resort) => (
                  <option key={resort.code} value={resort.code}>
                    {resort.name}
                  </option>
                ))}
            </optgroup>
          ))}
        </select>
      </label>
      <label>
        슬로프
        <select value={slopeId} onChange={(event) => setSlopeId(event.target.value)} disabled={!resortCode || slopes.length === 0}>
          <option value="">{resortCode && slopes.length === 0 ? "불러오는 중" : "슬로프 고르기"}</option>
          {slopes
            .filter((slope) => !exclude.includes(slope.id))
            .map((slope) => (
              <option key={slope.id} value={slope.id}>
                {slope.name}
              </option>
            ))}
        </select>
      </label>
      <button
        type="button"
        disabled={!slopeId}
        onClick={() => {
          onAdd(Number(slopeId));
          setSlopeId("");
        }}
      >
        비교에 추가
      </button>
    </div>
  );
}
