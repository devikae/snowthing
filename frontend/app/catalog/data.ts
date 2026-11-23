import { cache } from "react";

// 카탈로그는 백엔드 없이 CDN의 정적 파일(목록 index.json, 브랜드별 pages.json, layout.json, 페이지 이미지)을 읽는다.
// 로컬에서는 NEXT_PUBLIC_CATALOG_BASE_URL로 로컬 정적 서버를 가리킨다.
export const CATALOG_BASE_URL = (
  process.env.NEXT_PUBLIC_CATALOG_BASE_URL || "https://images.snowthing.org/public/catalogs"
).replace(/\/$/, "");

export type CatalogCategory = "데크" | "바인딩" | "부츠";

export interface CatalogSection {
  key: string;
  label: string;
  start: number;
  end: number;
}

interface CatalogBrandBase {
  code: string;
  name: string;
  origin: string;
  categories: CatalogCategory[];
}

/** PDF를 페이지 이미지로 바꿔 둔 브랜드 */
export interface CatalogBook extends CatalogBrandBase {
  type: "PAGES";
  sourceUrl: string;
  sourceLabel: string;
  pageCount: number;
  hasText: boolean;
  lang: "en" | "ja" | null;
  sections: CatalogSection[];
  hasLayout: boolean;
}

/** 공개 PDF가 없어 공식 페이지로 연결하는 브랜드 */
export interface CatalogLink extends CatalogBrandBase {
  type: "LINK" | "HOME";
  linkUrl: string;
  linkLabel: string;
}

export type CatalogBrand = CatalogBook | CatalogLink;

export interface CatalogIndex {
  season: string;
  brands: CatalogBrand[];
}

/** 페이지 한 장: 이미지 크기와 PDF에서 뽑은 원문 */
export interface CatalogPage {
  no: number;
  w: number;
  h: number;
  text: string;
}

/** 원문을 원본 자리에 놓기 위한 줄 정보. 좌표와 글자 크기는 페이지 크기 대비 비율 */
export interface CatalogLine {
  x: number;
  y: number;
  s: number;
  b: 0 | 1;
  v: 0 | 1;
  t: string;
}

const EMPTY_INDEX: CatalogIndex = { season: "26-27", brands: [] };

export const getCatalogIndex = cache(async (): Promise<CatalogIndex> => {
  try {
    const response = await fetch(`${CATALOG_BASE_URL}/index.json`, {
      next: { revalidate: 3600 },
      signal: AbortSignal.timeout(3000),
    });
    if (!response.ok) return EMPTY_INDEX;
    return (await response.json()) as CatalogIndex;
  } catch {
    return EMPTY_INDEX;
  }
});

export function isBook(brand: CatalogBrand): brand is CatalogBook {
  return brand.type === "PAGES";
}

export function pageImageUrl(code: string, no: number, thumb = false) {
  return `${CATALOG_BASE_URL}/${code}/${thumb ? "t" : "p"}${String(no).padStart(3, "0")}.webp`;
}
