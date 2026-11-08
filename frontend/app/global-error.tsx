"use client";

// 루트 레이아웃까지 실패하면 레이아웃과 전역 CSS 없이 이 화면만 그려진다. 스타일을 직접 넣는다.
export default function GlobalError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="ko">
      <body style={{ margin: 0, minHeight: "100vh", display: "grid", placeItems: "center", fontFamily: "system-ui, sans-serif", background: "#fff", color: "#111" }}>
        <main style={{ maxWidth: 360, padding: 24, textAlign: "center" }}>
          <h1 style={{ fontSize: 22, margin: "0 0 12px" }}>화면을 표시하지 못했어요</h1>
          <p style={{ margin: "0 0 24px", color: "#555", lineHeight: 1.6 }}>일시적인 문제일 수 있어요. 잠시 후 다시 시도해 주세요.</p>
          <div style={{ display: "flex", gap: 12, justifyContent: "center" }}>
            <button type="button" onClick={reset} style={{ padding: "12px 20px", border: 0, borderRadius: 8, background: "#111", color: "#fff", fontSize: 16 }}>다시 시도</button>
            {/* eslint-disable-next-line @next/next/no-html-link-for-pages -- 레이아웃이 깨진 상태라 전체 새로고침으로 홈에 간다. */}
            <a href="/" style={{ padding: "12px 20px", border: "1px solid #ccc", borderRadius: 8, color: "#111", textDecoration: "none", fontSize: 16 }}>홈으로 가기</a>
          </div>
        </main>
      </body>
    </html>
  );
}
