import Link from "next/link";
import { Suspense } from "react";
import { PageHeading } from "../../components/PageHeading";
import { Footer, TopNav } from "../../components/SiteChrome";
import { publicPageMetadata } from "../../lib/siteMetadata";
import { getSlopeResorts } from "../data";
import { CompareView } from "../CompareView";
import styles from "../slopeSpecs.module.css";

export const metadata = publicPageMetadata(
  "슬로프 비교",
  "국내와 일본 슬로프 2~3개의 길이, 평균 경사, 최대 경사, 폭, 코스 맵을 공식 제원으로 나란히 비교합니다.",
  "/slopes/compare",
);

export default async function SlopeComparePage() {
  const resorts = await getSlopeResorts();
  return (
    <div className="community-page">
      <TopNav />
      <main className="community-container">
        <PageHeading
          breadcrumb={<Link href="/slopes">슬로프</Link>}
          title="슬로프 비교"
          description="슬로프를 2~3개 고르면 코스 맵과 공식 제원을 나란히 비교합니다. 국내와 일본 슬로프를 섞어 고를 수 있습니다."
        />
        {resorts === null ? (
          <p className={styles.notice}>리조트 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
        ) : (
          <Suspense fallback={null}>
            <CompareView resorts={resorts} />
          </Suspense>
        )}
      </main>
      <Footer />
    </div>
  );
}
