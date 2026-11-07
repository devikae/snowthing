import type { Metadata } from "next";
import { API_V1_URL } from "./api";
import { DEFAULT_OG_IMAGE, SITE_DESCRIPTION, SITE_NAME, SITE_URL } from "./siteMetadata";

// 카카오톡과 검색엔진 크롤러는 JS를 실행하지 않는다. 상세 페이지의 제목과 설명을 서버에서 만든다.
const PREVIEW_TIMEOUT_MS = 2000;
const DESCRIPTION_LENGTH = 120;

export async function fetchPreview<T>(path: string, headers?: Record<string, string>): Promise<T | null> {
  try {
    const response = await fetch(`${API_V1_URL}${path}`, { cache: "no-store", headers, signal: AbortSignal.timeout(PREVIEW_TIMEOUT_MS) });
    if (!response.ok) return null;
    return (await response.json()) as T;
  } catch {
    return null;
  }
}

// 본문은 마크다운이며 HTML이 섞일 수 있다. 미리보기용 평문으로 바꾼다.
export function plainTextSummary(markdown: string | null | undefined, maxLength = DESCRIPTION_LENGTH): string {
  if (!markdown) return "";
  const text = markdown
    .replace(/!\[[^\]]*\]\([^)]*\)/g, " ")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/[#>*_`~|]+/g, " ")
    .replace(/^\s*[-+]\s+/gm, " ")
    .replace(/\s+/g, " ")
    .trim();
  return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text;
}

// 첨부 사진이 없으면 본문 첫 사진을 쓴다. base64 사진은 미리보기 이미지로 쓸 수 없어 건너뛴다.
export function firstImageUrl(images: string[] | null | undefined, markdown?: string | null): string | null {
  const attached = images?.find((url) => /^https?:\/\//.test(url));
  if (attached) return attached;
  const match = markdown?.match(/!\[[^\]]*\]\((https?:\/\/[^)\s]+)\)/) ?? markdown?.match(/<img[^>]+src=["'](https?:\/\/[^"']+)["']/);
  return match ? match[1] : null;
}

export function detailMetadata({ title, description, path, image }: { title: string; description: string; path: string; image?: string | null }): Metadata {
  const pageTitle = `${title} | 눈팅 SnowThing`;
  const url = new URL(path, SITE_URL).toString();
  const summary = description || SITE_DESCRIPTION;
  const images = image ? [{ url: image }] : [DEFAULT_OG_IMAGE];
  return {
    title: pageTitle,
    description: summary,
    alternates: { canonical: url },
    openGraph: { type: "article", title: pageTitle, description: summary, url, siteName: SITE_NAME, locale: "ko_KR", images },
    twitter: { card: "summary_large_image", title: pageTitle, description: summary, images: images.map((item) => item.url) },
  };
}
