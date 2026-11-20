"use client";

import Link from "next/link";
import { OpeningDdayTag, ResortWeatherChip } from "../resort-cam/ResortHeaderInfo";
import camStyles from "../resort-cam/resortCam.module.css";
import { COUNTRY_LABEL, resortSlug, type ResortInfo } from "./specs";

// 슬로프캠 리조트 머리(지역 칩, 이름, D-DAY, 기온, 버튼)와 같은 모양을 쓴다. 두 화면을 오가도 같은 리조트로 보이게 한다.
type Props = {
  resort: ResortInfo;
  title: string;
  chips: string[];
  hasCam: boolean;
  backToResort?: boolean;
};

export function SlopeResortHeader({ resort, title, chips, hasCam, backToResort = false }: Props) {
  const slug = resortSlug(resort.code);
  const domestic = resort.country === "KR";
  return (
    <header className={camStyles.resortHeader}>
      <div>
        <div className={camStyles.resortMeta}>
          <span className={camStyles.metaRegion}>{resort.region}</span>
          {!domestic && <span className={camStyles.officialMeta}>{COUNTRY_LABEL[resort.country]}</span>}
          {chips.map((chip) => (
            <span key={chip} className={camStyles.metaCount}>
              {chip}
            </span>
          ))}
        </div>
        <div className={camStyles.resortTitle}>
          <h2>{title}</h2>
          {domestic && <OpeningDdayTag resortCode={resort.code} />}
        </div>
      </div>
      <div className={camStyles.resortHeaderActions}>
        {domestic && <ResortWeatherChip resortCode={resort.code} />}
        {backToResort && (
          <Link className={camStyles.backLink} href={`/slopes/${slug}`}>
            <span className="material-symbols-outlined" aria-hidden="true">landscape</span>
            {resort.name} 전체
          </Link>
        )}
        {domestic && (
          <Link className={camStyles.backLink} href={`/slopes#${slug}`}>
            <span className="material-symbols-outlined" aria-hidden="true">downhill_skiing</span>
            슬로프 현황
          </Link>
        )}
        {hasCam && (
          <Link className={camStyles.backLink} href={`/resort-cam/${slug}`}>
            <span className="material-symbols-outlined" aria-hidden="true">videocam</span>
            슬로프캠
          </Link>
        )}
        <Link className={camStyles.backLink} href="/slopes/compare">
          <span className="material-symbols-outlined" aria-hidden="true">compare_arrows</span>
          슬로프 비교
        </Link>
      </div>
    </header>
  );
}
