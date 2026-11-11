import type { Metadata, Viewport } from "next";
import { SITE_URL, SITE_NAME, SITE_TITLE, SITE_DESCRIPTION, DEFAULT_OG_IMAGE } from "./lib/siteMetadata";
import "@toast-ui/editor/dist/toastui-editor.css";
import "./globals.css";
import "./carpool.css";
import "./detailForms.css";
import "./motion.css";

/* eslint-disable @next/next/no-page-custom-font -- App Router의 최상위 레이아웃에서 모든 화면에 공통 폰트를 로드합니다. */

const themeBootScript = `
(() => {
  try {
    const saved = localStorage.getItem("snowthing-theme");
    const theme = saved === "light" || saved === "dark"
      ? saved
      : (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    document.documentElement.dataset.theme = theme;
  } catch {
    document.documentElement.dataset.theme = matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
})();`;

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: SITE_TITLE,
  description: SITE_DESCRIPTION,
  applicationName: "눈팅 SnowThing",
  openGraph: { type: "website", title: SITE_TITLE, description: SITE_DESCRIPTION, siteName: SITE_NAME, locale: "ko_KR", images: [DEFAULT_OG_IMAGE] },
  twitter: { card: "summary_large_image", title: SITE_TITLE, description: SITE_DESCRIPTION, images: [DEFAULT_OG_IMAGE.url] },
  verification: {
    google: process.env.GOOGLE_SITE_VERIFICATION || undefined,
    other: process.env.NAVER_SITE_VERIFICATION ? { "naver-site-verification": process.env.NAVER_SITE_VERIFICATION } : {},
  },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#09090b" },
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeBootScript }} />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Geist+Mono:wght@400;500;600;700&family=Plus+Jakarta+Sans:wght@600;700;800&display=swap"
          rel="stylesheet"
        />
        {/* 가변 폰트를 글자 범위별로 나눠, 화면에 쓰인 글자 조각만 받는다. 굵기별 정적 파일(각 약 770KB)을 받지 않는다. */}
        <link
          href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css"
          rel="stylesheet"
          crossOrigin="anonymous"
        />
        {/* 굵기와 채움 축을 쓰지 않아 기본 인스턴스만 받는다(1.1MB에서 0.3MB). */}
        {/* eslint-disable-next-line @next/next/google-font-display -- 아이콘 폰트는 받기 전에 아이콘 이름이 글자로 보이면 안 되어 block을 쓴다. */}
        <link
          href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined&display=block"
          rel="stylesheet"
        />
      </head>
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}
