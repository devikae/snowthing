import type { Metadata } from "next";
import Link from "next/link";
import { Footer, TopNav } from "./components/SiteChrome";

export const metadata: Metadata = { title: "페이지를 찾을 수 없어요 | 눈팅 SnowThing", robots: { index: false } };

export default function NotFound() {
  return (
    <>
      <TopNav active="home" />
      <main className="community-container not-found-page">
        <h1>페이지를 찾을 수 없어요</h1>
        <p>주소가 바뀌었거나 삭제된 페이지예요.</p>
        <Link href="/" className="snow-btn-primary">홈으로 가기</Link>
      </main>
      <Footer />
    </>
  );
}
