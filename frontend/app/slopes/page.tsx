import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { Footer, TopNav } from "../components/SiteChrome";
import { getSlopeResorts } from "./data";
import { COUNTRY_LABEL, resortSlug, type Country } from "./specs";
import styles from "./slopeSpecs.module.css";

// 슬로프맵 입구. 예전에는 리조트 공식 사이트에서 읽어 온 오늘 운영 현황판이었는데, 리조트마다 바뀌는 사이트를
// 계속 따라가며 고칠 수 없어 내렸다(2026-11-24). 이제는 리조트별 슬로프 제원과 코스 맵으로 가는 목록만 둔다.
const COUNTRIES: Country[] = ["KR", "JP"];

export default async function SlopesPage() {
  const resorts = await getSlopeResorts();

  return (
    <div className="community-page">
      <TopNav />
      <main className="community-container slopes-page">
        <PageHeading
          title="슬로프맵"
          description="리조트별 코스 맵과 공식 슬로프 길이, 경사를 봅니다. 슬로프끼리 나란히 비교할 수도 있어요."
          actions={<Link href="/slopes/compare">슬로프 비교</Link>}
        />

        {!resorts ? (
          <p className={styles.mapEmpty}>리조트 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
        ) : (
          <section className={`${styles.specEntry} ${styles.specIndex}`} aria-label="리조트별 슬로프맵">
            {COUNTRIES.map((country) => {
              const list = resorts.filter((resort) => resort.country === country);
              return list.length === 0 ? null : (
                <div key={country} className={styles.specLinks}>
                  <span className={styles.pickLabel}>{COUNTRY_LABEL[country]}</span>
                  {list.map((resort) => (
                    <Link key={resort.code} href={`/slopes/${resortSlug(resort.code)}`}>
                      {resort.name}
                    </Link>
                  ))}
                </div>
              );
            })}
          </section>
        )}
      </main>
      <Footer />
    </div>
  );
}
