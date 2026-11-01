import type { Metadata } from "next";

export const SITE_URL = "https://snowthing.org";
export const SITE_NAME = "눈팅";
export const SITE_TITLE = "눈팅 SnowThing | 스노보드 커뮤니티";
export const SITE_DESCRIPTION = "눈팅(SnowThing)은 스노보드 커뮤니티입니다. 슬로프캠, 오늘의 설질, 카풀·동행과 장비 정보를 함께 나눠요.";

export function publicPageMetadata(title: string, description: string, path: string): Metadata {
  const pageTitle = `${title} | 눈팅 SnowThing`;
  const url = new URL(path, SITE_URL).toString();
  return {
    title: pageTitle,
    description,
    alternates: { canonical: url },
    openGraph: { type: "website", title: pageTitle, description, url, siteName: SITE_NAME, locale: "ko_KR" },
    twitter: { card: "summary", title: pageTitle, description },
  };
}

export const PRIVATE_PAGE_ROBOTS: Metadata["robots"] = { index: false, follow: true };

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
