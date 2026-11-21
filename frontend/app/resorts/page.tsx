import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { Footer, TopNav } from "../components/SiteChrome";
import { kstDateKey } from "../lib/seasonOpening";
import { publicPageMetadata } from "../lib/siteMetadata";
import { getResortCameras } from "../resort-cam/data";
import { resortSlug } from "../resort-cam/types";
import { getOpenings, getSlopeBoard } from "./data";
import { openingSummary, openingView } from "./format";
import styles from "./resorts.module.css";

// 요청마다 그린다. 오늘 날짜(D-DAY)가 빌드에 굳지 않게 하고, 데이터는 data.ts가 잠깐씩 보관한다.
export const dynamic = "force-dynamic";

export const metadata = publicPageMetadata(
  "스키장 개장일, 리조트별 정보 모아보기",
  "곤지암, 하이원, 모나용평, 휘닉스파크, 비발디파크 등 국내 스키장의 개장일과 슬로프 운영 현황을 한 화면에서 보고, 리조트별 기온, 슬로프캠, 설질 제보, 카풀, 시즌권 가격으로 이동하세요.",
  "/resorts",
);

export default async function ResortsPage() {
  const [resorts, openings, board] = await Promise.all([getResortCameras(), getOpenings(), getSlopeBoard()]);
  const today = kstDateKey(new Date());

  return (
    <div className="community-page">
      <TopNav active="resorts" />
      <main className="community-container slopes-page">
        <PageHeading
          title="리조트 정보"
          description="국내 스키장의 개장일과 슬로프 운영 현황입니다. 리조트를 누르면 기온, 슬로프캠, 오늘의 설질, 카풀, 시즌권 가격을 한 화면에서 볼 수 있어요."
        />
        <ul className={styles.list}>
          {resorts.map((resort) => {
            const opening = openingView(openings?.resorts.find((item) => item.resortCode === resort.code), today);
            const status = board?.find((item) => item.resortCode === resort.code);
            const slopeText = !status?.supported
              ? null
              : status.collectedAt
                ? `${status.openSlopeCount ?? 0} / ${status.totalSlopeCount ?? status.slopes.length}면 운영`
                : "아직 받은 현황이 없습니다";
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
                    {slopeText && (
                      <div>
                        <dt>슬로프</dt>
                        <dd>{slopeText}</dd>
                      </div>
                    )}
                    <div>
                      <dt>슬로프캠</dt>
                      <dd>{resort.cameras.length}개</dd>
                    </div>
                  </dl>
                </Link>
              </li>
            );
          })}
        </ul>
        <p className={styles.footnote}>
          개장일은 리조트 발표를 기준으로 하고, 발표 전인 곳은 예상일로 표시합니다. 슬로프 운영 현황은 리조트 공식 사이트에 공개된 값을 하루
          몇 번 읽어 옵니다.
        </p>
      </main>
      <Footer />
    </div>
  );
}
