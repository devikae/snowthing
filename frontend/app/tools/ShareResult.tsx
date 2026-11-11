"use client";
import { useState } from "react";
import KakaoShareButton from "../components/KakaoShareButton";

export default function ShareResult({ path, search, text }: { path: string; search: string; text: string }) {
  const [url, setUrl] = useState("");
  const [notice, setNotice] = useState("");
  const share = async () => {
    const resultUrl = new URL(path, window.location.origin); resultUrl.hash = search;
    setUrl(resultUrl.toString()); setNotice("");
    try {
      if (navigator.share) { await navigator.share({ title: "Snowthing 계산 결과", text, url: resultUrl.toString() }); setNotice("공유 창을 열었습니다."); }
      else { await navigator.clipboard.writeText(resultUrl.toString()); setNotice("결과 링크를 복사했습니다."); }
    } catch (error) {
      setNotice(error instanceof DOMException && error.name === "AbortError" ? "공유를 취소했습니다." : "아래 링크를 선택해서 복사해 주세요.");
    }
  };
  return <div className="tool-share"><div className="tool-share-actions"><button type="button" onClick={() => void share()}>계산 결과 공유</button><KakaoShareButton title="Snowthing 계산 결과" description={text} url={`${path}#${search}`} buttonTitle="계산 결과 보기" onFallback={share} /></div><p>공유 링크에는 계산에 사용한 입력값이 포함됩니다.</p>{url && <label>결과 링크<input readOnly value={url} onFocus={event => event.target.select()} /></label>}{notice && <p role="status">{notice}</p>}</div>;
}
