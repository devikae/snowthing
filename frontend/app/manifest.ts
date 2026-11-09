import type { MetadataRoute } from "next";
import { SITE_DESCRIPTION } from "./lib/siteMetadata";

// 홈 화면에 추가하면 주소창 없는 앱 창(standalone)으로 열린다. iOS는 이 상태에서만 웹 푸시를 받을 수 있다.
export default function manifest(): MetadataRoute.Manifest {
  return {
    id: "/",
    name: "눈팅 SnowThing",
    short_name: "눈팅",
    description: SITE_DESCRIPTION,
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#111111",
    lang: "ko",
    icons: [
      { src: "/icon.svg", sizes: "any", type: "image/svg+xml" },
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
      { src: "/icons/maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
      { src: "/apple-icon.png", sizes: "180x180", type: "image/png" },
    ],
  };
}
