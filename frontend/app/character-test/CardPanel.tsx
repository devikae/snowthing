"use client";

import { useEffect, useState } from "react";
import type { CharacterResult } from "./api";
import { renderCard } from "./cardImage";
import type { Sport } from "./data";
import { shareOrSaveImage } from "./shareFile";

// 공유 창은 클릭 직후에만 열리는 브라우저가 있어 카드를 미리 그려 둔다.
export function CardPanel({ result, sport }: { result: CharacterResult; sport: Sport }) {
  const [card, setCard] = useState<{ blob: Blob; url: string } | null>(null);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let cancelled = false;
    let createdUrl = "";
    renderCard(result, sport)
      .then((blob) => {
        if (cancelled) return;
        createdUrl = URL.createObjectURL(blob);
        setCard({ blob, url: createdUrl });
      })
      .catch(() => {
        if (!cancelled) setNotice("카드를 만들지 못했습니다. 새로고침해 주세요.");
      });
    return () => {
      cancelled = true;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [result, sport]);

  const share = async () => {
    if (!card) return;
    const outcome = await shareOrSaveImage(card.blob, `snowthing-character-${result.character.job}.png`, "내 스키장 캐릭터");
    setNotice(outcome === "shared" ? "공유 창을 열었어요." : outcome === "saved" ? "카드 이미지를 저장했어요." : "공유를 취소했어요.");
  };

  return (
    <section className="ct-panel ct-card" aria-labelledby="ct-card-title">
      <h2 id="ct-card-title">내 캐릭터 카드</h2>
      {card
        // eslint-disable-next-line @next/next/no-img-element -- 브라우저에서 만든 blob 주소라 이미지 최적화를 거칠 수 없다.
        ? <img className="ct-card-preview" src={card.url} width={270} height={480} alt="캐릭터 이름과 설명, 스탯이 적힌 세로 카드" />
        : <div className="ct-card-preview ct-card-loading" aria-hidden="true" />}
      <button type="button" className="ct-primary" onClick={() => void share()} disabled={!card}>카드 공유 또는 저장</button>
      <p className="ct-fineprint" role="status">{notice || "지원되는 폰은 공유 창이 열리고, 아니면 이미지로 저장돼요."}</p>
    </section>
  );
}
