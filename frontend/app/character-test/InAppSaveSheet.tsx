"use client";

import { useState } from "react";
import { openInExternalBrowser } from "./shareFile";

// 앱 안 브라우저는 다운로드를 막지만 화면에 뜬 이미지를 길게 눌러 저장하는 건 대부분 된다.
// blob 주소는 길게 눌러도 저장 메뉴가 안 뜨는 앱이 있어 data 주소로 띄운다.
export function InAppSaveSheet({ imageUrl, alt, externalUrl, onClose }: { imageUrl: string; alt: string; externalUrl: string; onClose: () => void }) {
  const [notice, setNotice] = useState("");

  const openExternal = async () => {
    if (openInExternalBrowser(externalUrl)) return;
    try {
      await navigator.clipboard.writeText(externalUrl);
      setNotice("링크를 복사했어요. 사파리나 크롬에 붙여 넣어 열어 주세요.");
    } catch {
      setNotice("오른쪽 아래(또는 위) 메뉴에서 '다른 브라우저로 열기'를 눌러 주세요.");
    }
  };

  return (
    <div className="ct-save-sheet" role="dialog" aria-modal="true" aria-label="이미지 저장">
      <div className="ct-save-sheet-body">
        <p className="ct-save-sheet-title">이미지를 길게 눌러 저장하세요</p>
        {/* eslint-disable-next-line @next/next/no-img-element -- 브라우저에서 만든 data 주소라 이미지 최적화를 거칠 수 없다. */}
        <img src={imageUrl} alt={alt} className="ct-save-sheet-image" />
        <p className="ct-fineprint">저장 메뉴가 안 뜨면 다른 브라우저로 열어서 저장해 주세요.</p>
        <button type="button" className="ct-primary" onClick={() => void openExternal()}>다른 브라우저로 열기</button>
        {notice && <p className="ct-fineprint" role="status">{notice}</p>}
        <button type="button" className="ct-text-button" onClick={onClose}>닫기</button>
      </div>
    </div>
  );
}
