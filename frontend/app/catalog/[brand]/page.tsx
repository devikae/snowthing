import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { PageHeading } from "../../components/PageHeading";
import { publicPageMetadata } from "../../lib/siteMetadata";
import { CatalogViewer } from "../CatalogViewer";
import { getCatalogIndex, isBook } from "../data";
import styles from "../catalog.module.css";

interface CatalogBrandPageProps {
  params: Promise<{ brand: string }>;
  searchParams: Promise<{ section?: string }>;
}

async function findBook(code: string) {
  const index = await getCatalogIndex();
  const brand = index.brands.find((item) => item.code === code);
  return brand && isBook(brand) ? { book: brand, season: index.season } : null;
}

export async function generateMetadata({ params }: CatalogBrandPageProps): Promise<Metadata> {
  const { brand } = await params;
  const found = await findBook(brand);
  if (!found) return publicPageMetadata("카탈로그", "26-27 스노보드 카탈로그", "/catalog");
  const { book, season } = found;
  return publicPageMetadata(
    `${book.name} ${season} 카탈로그`,
    `${book.name} ${season} 공식 카탈로그 ${book.pageCount}쪽을 넘겨 보세요. ${book.categories.join(", ")}`,
    `/catalog/${book.code}`,
  );
}

export default async function CatalogBrandPage({ params, searchParams }: CatalogBrandPageProps) {
  const [{ brand }, { section }] = await Promise.all([params, searchParams]);
  const found = await findBook(brand);
  if (!found) notFound();
  const { book, season } = found;
  const current = book.sections.find((item) => item.key === section);

  return (
    <main className={styles.page}>
      <PageHeading
        breadcrumb={<><Link href="/catalog">카탈로그</Link> / {book.name}</>}
        title={`${book.name} ${season}`}
        description={`${book.origin} | ${book.pageCount}쪽 | ${book.categories.join(", ")}`}
        actions={book.sourceUrl ? (
          <a className={styles.button} href={book.sourceUrl} target="_blank" rel="noopener noreferrer">
            원본 보기
            <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">open_in_new</span>
          </a>
        ) : undefined}
      />
      {book.sections.length > 0 && (
        <nav className={styles.tabs} aria-label="품목">
          <Link href={`/catalog/${book.code}`} className={current ? "" : styles.tabOn} aria-current={current ? undefined : "page"}>
            전체<small>{book.pageCount}</small>
          </Link>
          {book.sections.map((item) => (
            <Link key={item.key} href={`/catalog/${book.code}?section=${item.key}`} className={current === item ? styles.tabOn : ""} aria-current={current === item ? "page" : undefined}>
              {item.label}<small>{item.end - item.start + 1}</small>
            </Link>
          ))}
        </nav>
      )}
      <CatalogViewer key={current?.key ?? "all"} book={book} section={current ?? null} />
    </main>
  );
}
