"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { API_V1_URL } from "../lib/api";
import styles from "./slopeSpecs.module.css";
import {
  COUNTRY_LABEL,
  DIFFICULTY_LABEL,
  DIFFICULTY_ORDER,
  formatGradient,
  formatLength,
  resortSlug,
  type CompareItem,
  type Country,
  type ResortSummary,
} from "./specs";

const MAX_PICK = 3;

type CompareState = { status: "idle" } | { status: "loading" } | { status: "error" } | { status: "ready"; items: CompareItem[] };

export function CompareView({ resorts }: { resorts: ResortSummary[] }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const known = useMemo(() => new Set(resorts.map((resort) => resort.code)), [resorts]);
  const picked = useMemo(
    () =>
      Array.from(new Set((searchParams.get("codes") ?? "").split(",").filter((code) => known.has(code)))).slice(0, MAX_PICK),
    [searchParams, known],
  );
  const pickedKey = picked.join(",");
  const [result, setResult] = useState<{ key: string; state: CompareState } | null>(null);

  useEffect(() => {
    if (picked.length < 2) return;
    const controller = new AbortController();
    fetch(`${API_V1_URL}/slopes/compare?codes=${encodeURIComponent(pickedKey)}`, { signal: controller.signal })
      .then((response) => (response.ok ? (response.json() as Promise<{ resorts: CompareItem[] }>) : Promise.reject()))
      .then((data) => setResult({ key: pickedKey, state: { status: "ready", items: data.resorts } }))
      .catch(() => {
        if (!controller.signal.aborted) setResult({ key: pickedKey, state: { status: "error" } });
      });
    return () => controller.abort();
  }, [pickedKey, picked.length]);

  const state: CompareState =
    picked.length < 2 ? { status: "idle" } : result && result.key === pickedKey ? result.state : { status: "loading" };

  const toggle = (code: string) => {
    const next = picked.includes(code) ? picked.filter((item) => item !== code) : [...picked, code].slice(0, MAX_PICK);
    router.replace(next.length ? `${pathname}?codes=${next.join(",")}` : pathname, { scroll: false });
  };

  const groups: Country[] = ["KR", "JP"];

  return (
    <>
      {groups.map((country) => {
        const list = resorts.filter((resort) => resort.country === country);
        if (list.length === 0) return null;
        return (
          <div key={country} className={styles.pickGroup}>
            <span className={styles.pickLabel}>{COUNTRY_LABEL[country]}</span>
            <div className={styles.chips}>
              {list.map((resort) => {
                const on = picked.includes(resort.code);
                return (
                  <button
                    key={resort.code}
                    type="button"
                    aria-pressed={on}
                    disabled={!on && picked.length >= MAX_PICK}
                    onClick={() => toggle(resort.code)}
                  >
                    {resort.name}
                  </button>
                );
              })}
            </div>
          </div>
        );
      })}

      {state.status === "idle" && <p className={styles.notice}>비교할 리조트를 2곳 이상 골라 주세요. 최대 3곳까지 고를 수 있습니다.</p>}
      {state.status === "loading" && <p className={styles.notice}>비교 중입니다.</p>}
      {state.status === "error" && <p className={styles.notice}>비교 결과를 불러오지 못했습니다.</p>}
      {state.status === "ready" && <CompareTable items={state.items} />}
    </>
  );
}

function CompareTable({ items }: { items: CompareItem[] }) {
  const rows: { label: string; value: (item: CompareItem) => React.ReactNode }[] = [
    { label: "슬로프 수", value: (item) => `${item.slopeCount}개` },
    { label: "최장 슬로프", value: (item) => formatLength(item.longestLengthM) },
    { label: "슬로프 길이 합", value: (item) => formatLength(item.totalLengthM) },
    { label: "가장 가파른 최대 경사", value: (item) => formatGradient(item.steepestMaxGradient) },
    {
      label: "평균 경사의 평균",
      value: (item) =>
        item.meanAvgGradient == null ? "미공개" : `${formatGradient(item.meanAvgGradient)} (${item.avgGradientCount}개 기준)`,
    },
    {
      label: "난이도 분포",
      value: (item) => (
        <span className={styles.distribution}>
          {DIFFICULTY_ORDER.filter((key) => item.difficultyCounts[key] > 0).map((key) => (
            <span key={key}>
              <i className={styles.dot} data-difficulty={key} aria-hidden="true" />
              {DIFFICULTY_LABEL[key]} {item.difficultyCounts[key]}
            </span>
          ))}
        </span>
      ),
    },
  ];
  return (
    <div className={styles.tableWrap}>
      <table className={styles.compareTable}>
        <thead>
          <tr>
            <th scope="col">항목</th>
            {items.map((item) => (
              <th key={item.resort.code} scope="col">
                <Link href={`/slopes/${resortSlug(item.resort.code)}`}>{item.resort.name}</Link>
                <small>{item.resort.region}</small>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label}>
              <th scope="row">{row.label}</th>
              {items.map((item) => (
                <td key={item.resort.code}>{row.value(item)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <p className={styles.muted}>난이도 분포는 리조트 공식 등급을 초급, 중급, 상급, 최상급 네 묶음으로 나눈 것입니다. 리조트마다 등급 기준이 다릅니다.</p>
    </div>
  );
}
