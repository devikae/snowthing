import type { Metadata } from "next";

export const SITE_URL = "https://snowthing.org";
export const SITE_NAME = "눈팅";
export const SITE_TITLE = "눈팅 SnowThing | 윈터스포츠 커뮤니티";
export const SITE_DESCRIPTION = "눈팅(SnowThing)은 윈터스포츠 커뮤니티입니다. 슬로프캠, 오늘의 설질, 카풀과 동행, 장비 정보를 함께 나눠요.";

// Next는 하위 페이지의 openGraph가 상위 값을 통째로 덮는다. openGraph를 정하는 곳마다 이 이미지를 넣는다.
export const DEFAULT_OG_IMAGE = { url: "/og-default.png", width: 1200, height: 630, alt: "눈팅 SnowThing" };

// 목록과 문서처럼 주소 하나가 한 화면인 페이지용. url 없이 쓰려면 sectionOpenGraph를 쓴다.
export function sectionOpenGraph(title: string, description: string): Metadata {
  return {
    title,
    description,
    openGraph: { type: "website", title, description, siteName: SITE_NAME, locale: "ko_KR", images: [DEFAULT_OG_IMAGE] },
    twitter: { card: "summary_large_image", title, description, images: [DEFAULT_OG_IMAGE.url] },
  };
}

export function publicPageMetadata(title: string, description: string, path: string): Metadata {
  const pageTitle = `${title} | 눈팅 SnowThing`;
  const url = new URL(path, SITE_URL).toString();
  return {
    title: pageTitle,
    description,
    alternates: { canonical: url },
    openGraph: { type: "website", title: pageTitle, description, url, siteName: SITE_NAME, locale: "ko_KR", images: [DEFAULT_OG_IMAGE] },
    twitter: { card: "summary_large_image", title: pageTitle, description, images: [DEFAULT_OG_IMAGE.url] },
  };
}

export const PRIVATE_PAGE_ROBOTS: Metadata["robots"] = { index: false, follow: true };

// 검색엔진에 올리지 않는 화면도 탭 제목과 공유 미리보기에는 그 화면의 설명을 보여 준다.
export function privatePageMetadata(title: string, description: string): Metadata {
  return { ...sectionOpenGraph(`${title} | 눈팅 SnowThing`, description), robots: PRIVATE_PAGE_ROBOTS };
}

export const websiteStructuredData = {
  "@context": "https://schema.org",
  "@type": "WebSite",
  "@id": `${SITE_URL}/#website`,
  name: SITE_NAME,
  alternateName: ["SnowThing", "눈팅 SnowThing", "snowthing.org"],
  url: `${SITE_URL}/`,
  description: SITE_DESCRIPTION,
  inLanguage: "ko-KR",
};
