"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";

/** 후원 링크. 정해지면 이 값만 채운다. 비어 있으면 누를 때 준비 중 안내를 띄운다. */
const SUPPORT_URL = "";

/** 화면 오른쪽 아래 커피 후원 버튼. 관리자 화면에서는 숨긴다. */
export function SupportCoffeeButton() {
  const pathname = usePathname();
  const [notice, setNotice] = useState(false);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(false), 2500);
    return () => window.clearTimeout(timer);
  }, [notice]);

  if (pathname?.startsWith("/admin")) return null;

  const label = "눈팅에 커피 한 잔 후원하기";
  return (
    <div className="support-coffee">
      {notice && <p className="support-coffee-notice" role="status">후원 링크를 준비하고 있어요.</p>}
      {SUPPORT_URL ? (
        <a className="support-coffee-button" href={SUPPORT_URL} target="_blank" rel="noopener noreferrer" aria-label={label} title={label}>
          <span className="material-symbols-outlined" aria-hidden="true">coffee</span>
        </a>
      ) : (
        <button type="button" className="support-coffee-button" onClick={() => setNotice(true)} aria-label={label} title={label}>
          <span className="material-symbols-outlined" aria-hidden="true">coffee</span>
        </button>
      )}
    </div>
  );
}
