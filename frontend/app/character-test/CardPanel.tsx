"use client";

import { useEffect, useState, type ReactNode } from "react";
import type { CharacterResult } from "./api";
import { renderCard } from "./cardImage";
import { QUESTIONS, characterName, descriptionSentences, type Sport } from "./data";
import KakaoShareButton from "../components/KakaoShareButton";
import { shareOrSaveImage } from "./shareFile";

// 결과는 카드 한 장으로 보여 준다. 카드에 이름, 스탯, 설명, 찰떡 타입이 다 들어 있어 따로 그리지 않는다.
// 공유 창은 클릭 직후에만 열리는 브라우저가 있어 카드를 미리 그려 둔다.
export function CardPanel({ result, sport, answers, children }: { result: CharacterResult; sport: Sport; answers?: string; children?: ReactNode }) {
  const [card, setCard] = useState<{ blob: Blob; url: string } | null>(null);
  const [notice, setNotice] = useState("");
  const name = characterName(result.character);

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
        if (!cancelled) setNotice("카드를 만들지 못했어요. 새로고침해 주세요.");
      });
    return () => {
      cancelled = true;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [result, sport]);

  const share = async () => {
    if (!card) return;
    const outcome = await shareOrSaveImage(card.blob, `snowthing-character-${result.character.job}.png`, "내 스키장 캐릭터", {
      text: `나는 스키장에서 ${name}래. 너는 어떤 캐릭터야?`,
      url: new URL("/character-test", window.location.origin).toString(),
    });
    setNotice(outcome === "shared" ? "공유 창을 열었어요." : outcome === "saved" ? "카드 이미지를 저장했어요." : "공유를 취소했어요.");
  };

  const alt = [`${name} 캐릭터 카드.`, ...descriptionSentences(result.character), result.bestMatch ? `나와 찰떡인 타입은 ${characterName(result.bestMatch)}.` : ""].join(" ").trim();

  return (
    <section className="ct-panel ct-card" aria-labelledby="ct-card-title">
      <h2 id="ct-card-title">나의 스키장 캐릭터</h2>
      {card
        // eslint-disable-next-line @next/next/no-img-element -- 브라우저에서 만든 blob 주소라 이미지 최적화를 거칠 수 없다.
        ? <img className="ct-card-preview" src={card.url} width={270} height={480} alt={alt} />
        : <div className="ct-card-preview ct-card-loading" role="img" aria-label={`${name} 카드를 만드는 중`} />}
      <button type="button" className="ct-primary" onClick={() => void share()} disabled={!card}>카드 공유 또는 저장</button>
      <div className="ct-kakao-share">
        <KakaoShareButton
          title="나는 어떤 스키장 캐릭터일까"
          description={`나는 스키장에서 ${name}래. 너는 어떤 캐릭터야?`}
          url="/character-test"
          imageUrl="/character-test/og-test.png"
          buttonTitle="테스트 하러 가기"
        />
      </div>
      <p className="ct-fineprint" role="status">{notice || "지원되는 폰은 공유 창이 열리고, 아니면 이미지로 저장돼요."}</p>
      {answers && <div className="ct-card-evidence">
        <h3>이렇게 골랐어요</h3>
        <ul className="ct-evidence">
          {result.highlightQuestions.map((number) => {
            const question = QUESTIONS[number - 1];
            return <li key={number}><span>{question.situation}</span><strong>{answers[number - 1] === "A" ? question.a : question.b}</strong></li>;
          })}
        </ul>
        <p className="ct-fineprint">재미로 보는 결과예요. 고른 답으로 정해질 뿐, 사람을 진단하지 않아요.</p>
      </div>}
      {children && (
        <div className="ct-card-link">
          <p className="ct-fineprint">카드와 함께 테스트 링크가 가요. 인스타그램처럼 사진만 받는 앱이면 모임 링크를 따로 보내 주세요.</p>
          {children}
        </div>
      )}
    </section>
  );
}
