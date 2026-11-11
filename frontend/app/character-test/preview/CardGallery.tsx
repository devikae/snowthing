"use client";

import { useEffect, useState } from "react";
import { fetchResult } from "../api";
import { renderCard } from "../cardImage";
import { characterName, descriptionSentences, type Sport } from "../data";

// 축별 답을 3개씩 같게 고른 답변이다. 앞 세 글자가 직업, 마지막 글자가 계획형(A)과 즉흥형(B)을 정한다.
const JOB_ANSWERS = ["AAA", "AAB", "ABA", "ABB", "BAA", "BAB", "BBA", "BBB"];
const SAMPLE_ANSWERS = JOB_ANSWERS.flatMap((axes) => ["A", "B"].map((plan) => `${[...axes, plan].map((choice) => choice.repeat(3)).join("")}AAA`));

interface GalleryCard {
  url: string;
  name: string;
  bestMatch: string;
  sentences: string[];
}

async function buildCard(answers: string, sport: Sport): Promise<GalleryCard> {
  const result = await fetchResult(answers);
  const blob = await renderCard(result, sport);
  return {
    url: URL.createObjectURL(blob),
    name: characterName(result.character),
    bestMatch: result.bestMatch ? characterName(result.bestMatch) : "없음",
    sentences: descriptionSentences(result.character),
  };
}

export function CardGallery() {
  const [cards, setCards] = useState<GalleryCard[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const created: GalleryCard[] = [];
    // CSRF 토큰을 요청마다 새로 받아서, 동시에 보내면 서로 덮어써 403이 난다. 한 장씩 만든다.
    const build = async () => {
      for (const [index, answers] of SAMPLE_ANSWERS.entries()) {
        if (cancelled) return;
        const card = await buildCard(answers, index % 2 === 0 ? "SKI" : "BOARD");
        created.push(card);
        if (!cancelled) setCards([...created]);
      }
    };
    // 개발 모드의 이중 실행이 겹치지 않도록 다음 틱에 시작한다.
    const timer = setTimeout(() => {
      build().catch((reason) => {
        if (!cancelled) setError(reason instanceof Error ? reason.message : "카드를 만들지 못했어요. 다시 시도해 주세요.");
      });
    }, 0);
    return () => {
      cancelled = true;
      clearTimeout(timer);
      created.forEach((card) => URL.revokeObjectURL(card.url));
    };
  }, []);

  if (error) return <p className="ct-error" role="alert">{error}</p>;
  if (cards.length === 0) return <p className="ct-notice" role="status">카드를 만드는 중이에요.</p>;
  return (
    <>
      <p className="ct-fineprint">스탯은 축마다 답을 한쪽으로 고른 예시라 0칸 아니면 3칸으로 나와요. 실제 결과는 답에 따라 달라져요. 카드를 누르면 원본 크기로 열려요.</p>
      <ul className="ct-gallery">
        {cards.map((card) => (
          <li key={card.name}>
            <a href={card.url} target="_blank" rel="noreferrer">
              {/* eslint-disable-next-line @next/next/no-img-element -- 브라우저에서 만든 blob 주소라 이미지 최적화를 거칠 수 없다. */}
              <img src={card.url} width={270} height={480} alt={`${card.name} 카드`} />
            </a>
            <strong>{card.name}</strong>
            <span>찰떡: {card.bestMatch}</span>
            {card.sentences.map((sentence) => <p key={sentence}>{sentence}</p>)}
          </li>
        ))}
      </ul>
    </>
  );
}
