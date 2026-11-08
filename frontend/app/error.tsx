"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Footer, TopNav } from "./components/SiteChrome";

// 화면을 그리다 예외가 나면 Next 기본 영문 오류 화면 대신 이 화면을 보여 준다.
export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <>
      <TopNav active="home" />
      <main className="community-container not-found-page">
        <h1>화면을 표시하지 못했어요</h1>
        <p>일시적인 문제일 수 있어요. 다시 시도해도 같으면 잠시 후 들러 주세요.</p>
        <div style={{ display: "flex", gap: 12, justifyContent: "center", flexWrap: "wrap" }}>
          <button type="button" className="snow-btn-primary" onClick={reset}>다시 시도</button>
          <Link href="/" className="snow-btn-secondary">홈으로 가기</Link>
        </div>
      </main>
      <Footer />
    </>
  );
}
