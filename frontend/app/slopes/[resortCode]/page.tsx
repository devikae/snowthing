import Link from "next/link";
import { Footer, TopNav } from "../../components/SiteChrome";
import { publicPageMetadata } from "../../lib/siteMetadata";
import { getResortCameras } from "../../resort-cam/data";
import { requireResortDetail } from "../data";
import { ResortSlopesView } from "../ResortSlopesView";
import { SlopeResortHeader } from "../SlopeResortHeader";
import { COUNTRY_LABEL, resortSlug } from "../specs";
import styles from "../slopeSpecs.module.css";

export async function generateMetadata({ params }: { params: Promise<{ resortCode: string }> }) {
  const { resortCode } = await params;
  const { resort, slopes } = await requireResortDetail(resortCode);
  return publicPageMetadata(
    `${resort.name} 슬로프 제원과 코스 맵`,
    `${resort.region} ${resort.name} 슬로프 ${slopes.length}개의 공식 길이, 평균 경사, 최대 경사와 코스 맵을 봅니다.`,
    `/slopes/${resortSlug(resort.code)}`,
  );
}

export default async function SlopeResortPage({ params }: { params: Promise<{ resortCode: string }> }) {
  const { resortCode } = await params;
  const [detail, cameras] = await Promise.all([requireResortDetail(resortCode), getResortCameras()]);
  const { resort, slopes } = detail;
  const mapped = slopes.filter((slope) => slope.osmWayIds.length > 0).length;
  const hasCam = cameras.some((item) => item.code === resort.code);
  return (
    <div className="community-page">
      <TopNav active="resort" />
      <main className="community-container">
        <nav className={styles.breadcrumb} aria-label="위치">
          <Link href="/slopes">슬로프</Link> / <span>{COUNTRY_LABEL[resort.country]}</span> / <span>{resort.name}</span>
        </nav>
        <ResortSlopesView
          detail={detail}
          header={
            <SlopeResortHeader
              key="header"
              resort={resort}
              title={resort.name}
              chips={[`슬로프 ${slopes.length}개`, `코스 맵 ${mapped}개`]}
              hasCam={hasCam}
            />
          }
        />
        <p className={styles.footnote}>
          제원은 리조트 공식 자료로 정리했고, 공식 자료에 없는 값은 미공개로 둡니다. 코스 맵은 OpenStreetMap 선을 간략하게 그린 것이라 실제
          지형, 리조트 공식 슬로프맵과 다를 수 있습니다.
        </p>
      </main>
      <Footer />
    </div>
  );
}
