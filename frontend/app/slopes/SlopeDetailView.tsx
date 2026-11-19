"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import camStyles from "../resort-cam/resortCam.module.css";
import { CourseCard } from "./SlopeMapViews";
import styles from "./slopeSpecs.module.css";
import {
  difficultyText,
  formatGradient,
  formatLength,
  relationLabel,
  slopePath,
  type ResortDetail,
  type Similar,
  type SlopeDetail,
} from "./specs";

const METRIC_LABEL = { AVG: "평균", MAX: "최대" } as const;

type Props = { detail: SlopeDetail; resortDetail: ResortDetail; header: ReactNode };

export function SlopeDetailView({ detail, resortDetail, header }: Props) {
  const { resort, slope, neighbors, similar } = detail;
  const other = resort.country === "KR" ? "일본" : "국내";

  return (
    <>
      <section className={camStyles.resortSection}>
      {header}
      <div className={styles.detailGrid}>
        <div className={styles.detailMap}>
          <CourseCard
            resortCode={resort.code}
            slope={slope}
            slopes={resortDetail.slopes}
            connections={resortDetail.connections}
            big
          />
        </div>
        <aside className={styles.panel}>
          <span className={styles.badge} data-difficulty={slope.difficulty}>
            {difficultyText(slope, resort.country)}
          </span>
          <dl className={styles.specs}>
            <div>
              <dt>길이</dt>
              <dd>{formatLength(slope.lengthM)}</dd>
            </div>
            <div>
              <dt>평균 경사</dt>
              <dd>{formatGradient(slope.avgGradient)}</dd>
            </div>
            <div>
              <dt>최대 경사</dt>
              <dd>{formatGradient(slope.maxGradient)}</dd>
            </div>
            <div>
              <dt>폭</dt>
              <dd>{formatLength(slope.widthM)}</dd>
            </div>
            <div>
              <dt>올라가는 리프트</dt>
              <dd>{slope.liftName ?? "미공개"}</dd>
            </div>
          </dl>
          <p className={styles.source}>
            <a href={slope.sourceUrl} target="_blank" rel="noopener noreferrer">
              공식 자료
            </a>{" "}
            {slope.checkedAt} 확인
          </p>

          <h2 className={styles.panelTitle}>주변 슬로프</h2>
          {neighbors.length === 0 ? (
            <p className={styles.muted}>붙어 있는 코스 정보가 없습니다.</p>
          ) : (
            <ul className={styles.neighbors}>
              {neighbors.map((neighbor) => (
                <li key={neighbor.id}>
                  <i className={styles.bar} data-difficulty={neighbor.difficulty} aria-hidden="true" />
                  <Link href={slopePath(resort.code, neighbor.id)}>{neighbor.name}</Link>
                  <span>{relationLabel(neighbor.relation, neighbor.distanceM)}</span>
                </li>
              ))}
            </ul>
          )}
        </aside>
      </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>경사가 비슷한 {other} 슬로프</h2>
        <p className={styles.muted}>
          두 슬로프가 함께 공개한 경사끼리만 비교합니다. 평균 경사가 둘 다 있으면 평균, 없으면 최대 경사로 비교하고, 차이가 3° 넘게
          나면 보여 주지 않습니다.
        </p>
        {similar.length === 0 ? (
          <p className={styles.notice}>
            비교할 수 있는 {other} 슬로프가 없습니다. 이 슬로프나 상대 슬로프의 경사가 공식 자료에 없을 수 있습니다.
          </p>
        ) : (
          <ul className={styles.cards}>
            {similar.map((item) => (
              <li key={item.slope.id}>
                <SimilarCard item={item} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

function SimilarCard({ item }: { item: Similar }) {
  const metric = METRIC_LABEL[item.metric];
  return (
    <Link href={slopePath(item.resort.code, item.slope.id)} className={styles.card}>
      <CourseCard resortCode={item.resort.code} slope={item.slope} slopes={[item.slope]} connections={[]} />
      <div className={styles.cardBody}>
        <small>{item.resort.name}</small>
        <strong>{item.slope.name}</strong>
        <span className={styles.cardMeta}>
          {metric} {formatGradient(item.baseGradient)} vs {formatGradient(item.gradient)}{" "}
          <b>차이 {Number(item.gradientDiff).toFixed(1)}°</b>
        </span>
        <span className={styles.cardMeta}>
          <i className={styles.dot} data-difficulty={item.slope.difficulty} aria-hidden="true" />
          {difficultyText(item.slope, item.resort.country)} / {formatLength(item.slope.lengthM)}
        </span>
      </div>
    </Link>
  );
}
