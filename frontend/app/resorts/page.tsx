import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { Footer, TopNav } from "../components/SiteChrome";
import { kstDateKey } from "../lib/seasonOpening";
import { publicPageMetadata } from "../lib/siteMetadata";
import { getResortCameras } from "../resort-cam/data";
import { resortSlug } from "../resort-cam/types";
import { getOpenings } from "./data";
import { openingSummary, openingView } from "./format";
import styles from "./resorts.module.css";

// 요청마다 그린다. 오늘 날짜(D-DAY)가 빌드에 굳지 않게 하고, 데이터는 data.ts가 잠깐씩 보관한다.
export const dynamic = "force-dynamic";

export const metadata = publicPageMetadata(
  "스키장 개장일, 리조트별 정보 모아보기",
  "곤지암, 하이원, 모나용평, 휘닉스파크, 비발디파크 등 국내 스키장 개장일을 모았습니다. 리조트를 누르면 날씨, 슬로프캠, 오늘의 설질, 카풀, 시즌권 가격을 볼 수 있어요.",
  "/resorts",
);

export default async function ResortsPage() {
  const [resorts, openings] = await Promise.all([getResortCameras(), getOpenings()]);
  const today = kstDateKey(new Date());

  return (
    <div className="community-page">
      <TopNav />
      <main className="community-container slopes-page">
        <PageHeading
          title="리조트 정보"
          description="국내 스키장 개장일을 모았습니다. 리조트를 누르면 날씨, 슬로프캠, 오늘의 설질, 카풀, 시즌권 가격을 볼 수 있어요."
        />
        <ul className={styles.list}>
          {resorts.map((resort) => {
            const opening = openingView(openings?.resorts.find((item) => item.resortCode === resort.code), today);
            return (
              <li key={resort.code}>
                <Link href={`/resorts/${resortSlug(resort.code)}`} className={styles.listCard}>
                  <div className={styles.listHead}>
                    <span className={styles.region}>{resort.regionName}</span>
                    <h2>{resort.name}</h2>
                    {opening.kind === "upcoming" && (
                      <span className={styles.dday}>
                        {opening.dday}
                        {opening.confirmed ? null : <small>예상</small>}
                      </span>
                    )}
                  </div>
                  <dl className={styles.listFacts}>
                    <div>
                      <dt>개장일</dt>
                      <dd>{openingSummary(opening) ?? "아직 정보가 없습니다"}</dd>
                    </div>
                  </dl>
                </Link>
              </li>
            );
          })}
        </ul>
        <p className={styles.footnote}>
          개장일은 리조트 발표를 기준으로 하고, 발표 전인 곳은 예상일로 표시합니다.
        </p>
      </main>
      <Footer />
    </div>
  );
}
