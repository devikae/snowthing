import Link from "next/link";
import { Footer, TopNav } from "../../../components/SiteChrome";
import { publicPageMetadata } from "../../../lib/siteMetadata";
import { getResortCameras } from "../../../resort-cam/data";
import { requireResortDetail, requireSlopeDetail } from "../../data";
import { SlopeDetailView } from "../../SlopeDetailView";
import { SlopeResortHeader } from "../../SlopeResortHeader";
import { COUNTRY_LABEL, difficultyText, formatGradient, formatLength, resortSlug } from "../../specs";
import styles from "../../slopeSpecs.module.css";

type Params = { params: Promise<{ resortCode: string; slopeId: string }> };

export async function generateMetadata({ params }: Params) {
  const { resortCode, slopeId } = await params;
  const { resort, slope } = await requireSlopeDetail(resortCode, slopeId);
  return publicPageMetadata(
    `${resort.name} ${slope.name} 슬로프`,
    `${resort.name} ${slope.name}: 길이 ${formatLength(slope.lengthM)}, 평균 경사 ${formatGradient(slope.avgGradient)}, 최대 경사 ${formatGradient(slope.maxGradient)}. 코스 맵과 경사가 비슷한 ${resort.country === "KR" ? "일본" : "국내"} 슬로프를 봅니다.`,
    `/slopes/${resortSlug(resort.code)}/${slope.id}`,
  );
}

export default async function SlopeDetailPage({ params }: Params) {
  const { resortCode, slopeId } = await params;
  const [detail, resortDetail, cameras] = await Promise.all([
    requireSlopeDetail(resortCode, slopeId),
    requireResortDetail(resortCode),
    getResortCameras(),
  ]);
  const { resort, slope } = detail;
  const chips = [resort.name, difficultyText(slope, resort.country)];
  if (slope.nameLocal && slope.nameLocal !== slope.name) chips.push(slope.nameLocal);
  return (
    <div className="community-page">
      <TopNav />
      <main className="community-container">
        <nav className={styles.breadcrumb} aria-label="위치">
          <Link href="/slopes">슬로프</Link> / <span>{COUNTRY_LABEL[resort.country]}</span> /{" "}
          <Link href={`/slopes/${resortSlug(resort.code)}`}>{resort.name}</Link> / <span>{slope.name}</span>
        </nav>
        <SlopeDetailView
          detail={detail}
          resortDetail={resortDetail}
          header={
            <SlopeResortHeader
              key="header"
              resort={resort}
              title={slope.name}
              chips={chips}
              hasCam={cameras.some((item) => item.code === resort.code)}
              backToResort
            />
          }
        />
      </main>
      <Footer />
    </div>
  );
}
