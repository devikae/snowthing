import { PageHeading } from "../components/PageHeading";
import { publicPageMetadata } from "../lib/siteMetadata";
import { CatalogList } from "./CatalogList";
import { getCatalogIndex } from "./data";
import styles from "./catalog.module.css";

export const metadata = publicPageMetadata(
  "26-27 스노보드 카탈로그 모아보기",
  "국내 보드샵에서 파는 데크, 바인딩, 부츠 브랜드의 26-27 공식 카탈로그를 한곳에서 넘겨 보세요.",
  "/catalog",
);

export default async function CatalogPage() {
  const index = await getCatalogIndex();
  return (
    <main className={styles.page}>
      <PageHeading
        title={`${index.season} 카탈로그`}
        description="국내 보드샵에서 파는 브랜드의 공식 카탈로그를 한곳에 모았어요."
      />
      <CatalogList brands={index.brands} />
    </main>
  );
}
