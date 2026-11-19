import Link from "next/link";
import { Suspense } from "react";
import { PageHeading } from "../../components/PageHeading";
import { Footer, TopNav } from "../../components/SiteChrome";
import { publicPageMetadata } from "../../lib/siteMetadata";
import { getSlopeResorts } from "../data";
import { CompareView } from "../CompareView";
import styles from "../slopeSpecs.module.css";

export const metadata = publicPageMetadata(
  "스키장 슬로프 비교",
  "국내와 일본 스키장 2~3곳의 슬로프 수, 최장 길이, 최대 경사, 난이도 분포를 공식 제원으로 나란히 비교합니다.",
  "/slopes/compare",
);

export default async function SlopeComparePage() {
  const resorts = await getSlopeResorts();
  return (
    <div className="community-page">
      <TopNav active="resort" />
      <main className="community-container">
        <PageHeading
          breadcrumb={<Link href="/slopes">슬로프</Link>}
          title="스키장 비교"
          description="리조트를 2~3곳 고르면 공식 제원으로 나란히 비교합니다."
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
