"use client";

import Link from "next/link";
import { useMemo, useState, type ReactNode } from "react";
import camStyles from "../resort-cam/resortCam.module.css";
import { ResortMap } from "./ResortMap";
import { CourseCard } from "./SlopeMapViews";
import styles from "./slopeSpecs.module.css";
import {
  DIFFICULTY_LABEL,
  DIFFICULTY_ORDER,
  difficultyText,
  formatGradient,
  formatLength,
  slopePath,
  type Difficulty,
  type ResortDetail,
  type SlopeItem,
} from "./specs";

type SortKey = "default" | "length" | "avg" | "max";

const SORTS: { key: SortKey; label: string }[] = [
  { key: "default", label: "기본" },
  { key: "length", label: "긴 순" },
  { key: "avg", label: "평균 경사" },
  { key: "max", label: "최대 경사" },
];

// 값이 없는(미공개) 슬로프는 정렬할 때 항상 뒤로 보낸다.
function sortSlopes(slopes: SlopeItem[], key: SortKey) {
  if (key === "default") return slopes;
  const value = (slope: SlopeItem) =>
    key === "length" ? slope.lengthM : key === "avg" ? slope.avgGradient : slope.maxGradient;
  return [...slopes].sort((a, b) => {
    const va = value(a);
    const vb = value(b);
    if (va == null && vb == null) return a.id - b.id;
    if (va == null) return 1;
    if (vb == null) return -1;
    return Number(vb) - Number(va) || a.id - b.id;
  });
}

export function ResortSlopesView({ detail, header }: { detail: ResortDetail; header: ReactNode }) {
  const { resort, slopes, connections } = detail;
  const [sort, setSort] = useState<SortKey>("default");
  const [difficulty, setDifficulty] = useState<Difficulty | null>(null);
  const counts = useMemo(() => {
    const result = new Map<Difficulty, number>();
    for (const slope of slopes) result.set(slope.difficulty, (result.get(slope.difficulty) ?? 0) + 1);
    return result;
  }, [slopes]);
  const shown = useMemo(
    () => sortSlopes(slopes.filter((slope) => !difficulty || slope.difficulty === difficulty), sort),
    [slopes, difficulty, sort],
  );

  return (
    <>
      <section className={camStyles.resortSection}>
        {header}
        {/* 난이도 칩은 맵과 아래 카드 목록을 함께 거른다. 맵 바로 위에 둔다. */}
        <div className={`${styles.chips} ${styles.mapChips}`} role="group" aria-label="난이도">
          <button type="button" aria-pressed={difficulty === null} onClick={() => setDifficulty(null)}>
            전체 {slopes.length}
          </button>
          {DIFFICULTY_ORDER.filter((key) => counts.has(key)).map((key) => (
            <button key={key} type="button" aria-pressed={difficulty === key} onClick={() => setDifficulty(key)}>
              <i className={styles.dot} data-difficulty={key} aria-hidden="true" />
              {DIFFICULTY_LABEL[key]} {counts.get(key)}
            </button>
          ))}
        </div>
        <ResortMap resortCode={resort.code} slopes={slopes} difficulty={difficulty} />
      </section>

      <section className={styles.section}>
        <div className={styles.toolbar}>
          <label className={styles.sort}>
            정렬
            <select value={sort} onChange={(event) => setSort(event.target.value as SortKey)}>
              {SORTS.map((item) => (
                <option key={item.key} value={item.key}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
        </div>

        <ul className={styles.cards}>
          {shown.map((slope) => (
            <li key={slope.id}>
              <Link href={slopePath(resort.code, slope.id)} className={styles.card}>
                <CourseCard resortCode={resort.code} slope={slope} slopes={slopes} connections={connections} />
                <div className={styles.cardBody}>
                  <strong>{slope.name}</strong>
                  {slope.nameLocal && slope.nameLocal !== slope.name && <small>{slope.nameLocal}</small>}
                  <span className={styles.cardMeta}>
                    <i className={styles.dot} data-difficulty={slope.difficulty} aria-hidden="true" />
                    {difficultyText(slope, resort.country)} / {formatLength(slope.lengthM)}
                  </span>
                  <span className={styles.cardMeta}>
                    평균 {formatGradient(slope.avgGradient)} / 최대 {formatGradient(slope.maxGradient)}
                  </span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </>
  );
}
