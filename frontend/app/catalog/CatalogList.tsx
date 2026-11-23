"use client";

import Link from "next/link";
import { useState } from "react";
import { type CatalogBrand, type CatalogCategory, isBook, pageImageUrl } from "./data";
import styles from "./catalog.module.css";

type CategoryFilter = "전체" | CatalogCategory;
type OriginFilter = "전체" | "일본" | "해외";

const CATEGORY_FILTERS: CategoryFilter[] = ["전체", "데크", "바인딩", "부츠"];
const ORIGIN_FILTERS: OriginFilter[] = ["전체", "일본", "해외"];

function Segment<T extends string>({ label, options, value, onChange }: { label: string; options: T[]; value: T; onChange: (next: T) => void }) {
  return (
    <div className={styles.segment} role="group" aria-label={label}>
      {options.map((option) => (
        <button key={option} type="button" className={option === value ? styles.segmentOn : ""} aria-pressed={option === value} onClick={() => onChange(option)}>
          {option}
        </button>
      ))}
    </div>
  );
}

export function CatalogList({ brands }: { brands: CatalogBrand[] }) {
  const [category, setCategory] = useState<CategoryFilter>("전체");
  const [origin, setOrigin] = useState<OriginFilter>("전체");

  const matches = (brand: CatalogBrand) =>
    (category === "전체" || brand.categories.includes(category)) &&
    (origin === "전체" || (origin === "일본" ? brand.origin === "일본" : brand.origin !== "일본"));
  const books = brands.filter(isBook).filter(matches);
  const links = brands.filter((brand) => !isBook(brand)).filter(matches);

  if (brands.length === 0) {
    return <p className={styles.empty}>카탈로그를 불러오지 못했어요. 잠시 뒤 다시 열어 주세요.</p>;
  }

  return (
    <>
      <div className={styles.filters}>
        <Segment label="품목" options={CATEGORY_FILTERS} value={category} onChange={setCategory} />
        <Segment label="국가" options={ORIGIN_FILTERS} value={origin} onChange={setOrigin} />
        <span className={styles.count}>카탈로그 {books.length}개{links.length > 0 && `, 링크 ${links.length}개`}</span>
      </div>

      {books.length === 0 && links.length === 0 && <p className={styles.empty}>조건에 맞는 브랜드가 없어요.</p>}

      <div className={styles.grid}>
        {books.map((book) => (
          <Link key={book.code} href={`/catalog/${book.code}`} className={styles.card}>
            <div className={styles.cover}>
              {/* eslint-disable-next-line @next/next/no-img-element -- CDN에 올린 정적 표지라 이미지 최적화를 거치지 않는다. */}
              <img src={pageImageUrl(book.code, 1, true)} alt={`${book.name} 26-27 카탈로그 표지`} loading="lazy" />
            </div>
            <div className={styles.cardBody}>
              <h2>{book.name}</h2>
              <span className={styles.meta}>{book.origin} | {book.pageCount}쪽</span>
              <div className={styles.tags}>
                {book.categories.map((item) => <span key={item} className={styles.tag}>{item}</span>)}
                {!book.hasText && <span className={`${styles.tag} ${styles.tagImage}`}>이미지만</span>}
              </div>
            </div>
          </Link>
        ))}
      </div>

      {links.length > 0 && (
        <section className={styles.links} aria-labelledby="catalog-links-title">
          <h2 id="catalog-links-title">PDF가 없는 브랜드</h2>
          <p>공개 카탈로그가 없어서 공식 페이지로 연결해요.</p>
          <div className={styles.linkRows}>
            {links.map((link) => (
              <a key={link.code} href={link.linkUrl} target="_blank" rel="noopener noreferrer" className={styles.linkRow}>
                <span className={styles.mono} aria-hidden="true">{link.name.slice(0, 3)}</span>
                <span className={styles.linkText}>
                  <strong>{link.name}</strong>
                  <small>{link.origin} | {link.linkLabel}</small>
                </span>
                <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">open_in_new</span>
              </a>
            ))}
          </div>
        </section>
      )}
    </>
  );
}
