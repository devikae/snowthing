"use client";

import { usePathname } from "next/navigation";

/** 후원 링크. 정해지면 이 값만 채운다. 비어 있으면 버튼을 그리지 않는다. */
const SUPPORT_URL = "";

/** 화면 오른쪽 아래 커피 후원 버튼. 관리자 화면에서는 숨긴다. */
export function SupportCoffeeButton() {
  const pathname = usePathname();
  if (!SUPPORT_URL || pathname?.startsWith("/admin")) return null;

  const label = "눈팅에 커피 한 잔 후원하기";
  return (
    <div className="support-coffee">
      <a className="support-coffee-button" href={SUPPORT_URL} target="_blank" rel="noopener noreferrer" aria-label={label} title={label}>
        <span className="material-symbols-outlined" aria-hidden="true">coffee</span>
      </a>
    </div>
  );
}
