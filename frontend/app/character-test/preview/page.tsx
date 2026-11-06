import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { CardGallery } from "./CardGallery";

export const metadata: Metadata = { title: "카드 전체 보기", robots: { index: false, follow: false } };

// 문구와 카드를 검수하려는 개발 전용 화면이다. 운영 빌드에서는 404다.
export default function CardPreviewPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <><header className="ct-heading"><h1>카드 전체 보기</h1></header><CardGallery /></>;
}
