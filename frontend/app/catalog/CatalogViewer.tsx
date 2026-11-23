"use client";

import { useEffect, useState } from "react";
import { CATALOG_BASE_URL, type CatalogBook, type CatalogLine, type CatalogPage, type CatalogSection, pageImageUrl } from "./data";
import styles from "./catalog.module.css";

const LANG_NAME = { en: "영어", ja: "일본어" } as const;

interface ViewerData {
  pages: CatalogPage[];
  layout: CatalogLine[][] | null;
}

/** 원문을 원본과 같은 자리에 놓는다. 위치 정보가 없으면 줄글로 보여준다. */
function OriginalText({ page, lines, lang }: { page: CatalogPage; lines: CatalogLine[] | undefined; lang: string }) {
  if (!lines || lines.length === 0) return <div className={styles.plainText} lang={lang}>{page.text}</div>;
  return (
    <div className={styles.layoutText} lang={lang} style={{ aspectRatio: `${page.w} / ${page.h}` }}>
      {lines.map((line, index) => (
        <span
          key={index}
          className={`${line.b ? styles.bold : ""} ${line.v ? styles.vertical : ""}`}
          style={{ left: `${(line.x * 100).toFixed(2)}%`, top: `${(line.y * 100).toFixed(2)}%`, fontSize: `${(line.s * 100).toFixed(3)}cqw` }}
        >
          {line.t}
        </span>
      ))}
    </div>
  );
}

export function CatalogViewer({ book, section }: { book: CatalogBook; section: CatalogSection | null }) {
  const [data, setData] = useState<ViewerData | null>(null);
  const [failed, setFailed] = useState(false);
  const [openPages, setOpenPages] = useState<Set<number>>(new Set());
  const [tipClosed, setTipClosed] = useState(false);
  const [zoomPage, setZoomPage] = useState<CatalogPage | null>(null);
  const [thumbsOpen, setThumbsOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const [pagesResponse, layoutResponse] = await Promise.all([
          fetch(`${CATALOG_BASE_URL}/${book.code}/pages.json`),
          book.hasLayout ? fetch(`${CATALOG_BASE_URL}/${book.code}/layout.json`) : Promise.resolve(null),
        ]);
        if (!pagesResponse.ok) throw new Error("pages");
        const pagesJson = (await pagesResponse.json()) as { pages: CatalogPage[] };
        const layout = layoutResponse?.ok ? ((await layoutResponse.json()) as CatalogLine[][]) : null;
        if (!cancelled) setData({ pages: pagesJson.pages, layout });
      } catch {
        if (!cancelled) setFailed(true);
      }
    };
    void load();
    return () => { cancelled = true; };
  }, [book.code, book.hasLayout]);

  useEffect(() => {
    if (!zoomPage && !thumbsOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") { setZoomPage(null); setThumbsOpen(false); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [zoomPage, thumbsOpen]);

  if (failed) return <p className={styles.empty}>카탈로그를 불러오지 못했어요. 잠시 뒤 다시 열어 주세요.</p>;
  if (!data) return <p className={styles.empty}>카탈로그를 불러오는 중이에요.</p>;

  const pages = section ? data.pages.filter((page) => page.no >= section.start && page.no <= section.end) : data.pages;
  const textPages = pages.filter((page) => page.text);
  const allOpen = textPages.length > 0 && textPages.every((page) => openPages.has(page.no));
  const scrollToPage = (no: number) => document.getElementById(`catalog-page-${no}`)?.scrollIntoView();
  const togglePage = (no: number, open: boolean) =>
    setOpenPages((current) => {
      const next = new Set(current);
      if (open) next.add(no); else next.delete(no);
      return next;
    });

  return (
    <>
      <div className={styles.toolbar}>
        <select className={styles.button} aria-label="쪽 이동" onChange={(event) => scrollToPage(Number(event.target.value))}>
          {pages.map((page) => <option key={page.no} value={page.no}>{page.no}쪽</option>)}
        </select>
        <button type="button" className={styles.button} onClick={() => setThumbsOpen(true)}>쪽 목록</button>
        {textPages.length > 0 && (
          <button type="button" className={styles.button} onClick={() => setOpenPages(allOpen ? new Set() : new Set(textPages.map((page) => page.no)))}>
            {allOpen ? "원문 모두 접기" : "원문 모두 펼치기"}
          </button>
        )}
      </div>

      {!tipClosed && (
        <div className={`${styles.tip} ${book.hasText ? "" : styles.tipWarn}`}>
          <span>
            {book.hasText && book.lang
              ? `페이지 아래 원문 보기를 펼치고 브라우저 번역을 켜면 ${LANG_NAME[book.lang]} 원문을 한국어로 읽을 수 있어요.`
              : "이 카탈로그는 글자가 그림으로 들어 있어서 원문을 따로 보여주지 못해요. 페이지를 누르면 크게 볼 수 있어요."}
          </span>
          <button type="button" aria-label="안내 닫기" onClick={() => setTipClosed(true)}>
            <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">close</span>
          </button>
        </div>
      )}

      {pages.map((page) => (
        <section key={page.no} id={`catalog-page-${page.no}`} className={styles.pageBlock} aria-label={`${page.no}쪽`}>
          <div className={styles.pageNo}>{page.no} / {book.pageCount}</div>
          <button type="button" className={styles.pageImageButton} onClick={() => setZoomPage(page)} aria-label={`${page.no}쪽 크게 보기`}>
            {/* eslint-disable-next-line @next/next/no-img-element -- CDN에 올린 정적 페이지 이미지라 이미지 최적화를 거치지 않는다. */}
            <img src={pageImageUrl(book.code, page.no)} width={page.w} height={page.h} alt={`${book.name} 카탈로그 ${page.no}쪽`} loading="lazy" />
          </button>
          {page.text && (
            <details className={styles.original} open={openPages.has(page.no)} onToggle={(event) => togglePage(page.no, event.currentTarget.open)}>
              <summary>원문 보기</summary>
              {openPages.has(page.no) && <OriginalText page={page} lines={data.layout?.[page.no - 1]} lang={book.lang ?? "en"} />}
            </details>
          )}
        </section>
      ))}

      <p className={styles.credit}>출처: {book.name} 26-27 카탈로그, {book.sourceLabel}. 저작권은 각 브랜드에 있어요.</p>

      {zoomPage && (
        <div className={styles.zoom} role="dialog" aria-modal="true" aria-label={`${zoomPage.no}쪽 크게 보기`} onClick={() => setZoomPage(null)}>
          {/* eslint-disable-next-line @next/next/no-img-element -- CDN에 올린 정적 페이지 이미지라 이미지 최적화를 거치지 않는다. */}
          <img src={pageImageUrl(book.code, zoomPage.no)} alt={`${book.name} 카탈로그 ${zoomPage.no}쪽`} />
        </div>
      )}

      {thumbsOpen && (
        <div className={styles.thumbs} role="dialog" aria-modal="true" aria-label="쪽 목록">
          <button type="button" className={styles.thumbsClose} onClick={() => setThumbsOpen(false)}>닫기</button>
          <div className={styles.thumbGrid}>
            {pages.map((page) => (
              <button key={page.no} type="button" onClick={() => { setThumbsOpen(false); scrollToPage(page.no); }}>
                {/* eslint-disable-next-line @next/next/no-img-element -- CDN에 올린 정적 썸네일이라 이미지 최적화를 거치지 않는다. */}
                <img src={pageImageUrl(book.code, page.no, true)} alt="" loading="lazy" />
                {page.no}쪽
              </button>
            ))}
          </div>
        </div>
      )}
    </>
  );
}
