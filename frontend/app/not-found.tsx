import type { Metadata } from "next";
import Link from "next/link";
import { Footer, TopNav } from "./components/SiteChrome";

// 404 응답에는 Next.js가 noindex 메타를 자동으로 넣으므로 여기서 다시 넣지 않는다.
export const metadata: Metadata = { title: "페이지를 찾을 수 없어요 | 눈팅 SnowThing" };

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
