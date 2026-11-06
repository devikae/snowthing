"use client";

import { useState } from "react";
import { QUESTIONS, type Sport } from "./data";

function shuffledOrder() {
  const order = QUESTIONS.map((_, index) => index);
  for (let index = order.length - 1; index > 0; index -= 1) {
    const other = Math.floor(Math.random() * (index + 1));
    [order[index], order[other]] = [order[other], order[index]];
  }
  return order;
}

// 답변은 끝날 때까지 이 컴포넌트에만 있다. 묶음 이름은 보여 주지 않고, 문자열은 질문 번호 순서로 되돌려 넘긴다.
export function QuizFlow({ onComplete }: { onComplete: (answers: string, sport: Sport) => void }) {
  const [sport, setSport] = useState<Sport | null>(null);
  const [order] = useState(shuffledOrder);
  const [picked, setPicked] = useState<("A" | "B" | "C")[]>([]);

  if (!sport) {
    return (
      <section className="ct-panel ct-question">
        <p className="ct-eyebrow">시작하기</p>
        <h2>오늘은 뭘 타나요?</h2>
        <p className="ct-fineprint">결과에는 영향을 주지 않고 지도에 표시만 돼요.</p>
        <div className="ct-choices">
          <button type="button" onClick={() => setSport("SKI")}>스키</button>
          <button type="button" onClick={() => setSport("BOARD")}>보드</button>
        </div>
      </section>
    );
  }

  const step = picked.length;
  const question = QUESTIONS[order[step]];
  const choose = (choice: "A" | "B" | "C") => {
    const next = [...picked, choice];
    if (next.length < order.length) {
      setPicked(next);
      return;
    }
    const answers = Array.from<string>({ length: order.length });
    order.forEach((questionIndex, position) => { answers[questionIndex] = next[position]; });
    onComplete(answers.join(""), sport);
  };

  return (
    <section className="ct-panel ct-question">
      <div className="ct-progress" role="progressbar" aria-valuemin={0} aria-valuemax={order.length} aria-valuenow={step + 1} aria-label={`${order.length}개 중 ${step + 1}번째 질문`}><span style={{ width: `${((step + 1) / order.length) * 100}%` }} /></div>
      <p className="ct-eyebrow">{step + 1} / {order.length}</p>
      <h2>{question.situation}</h2>
      <div className="ct-choices">
        <button type="button" onClick={() => choose("A")}>{question.a}</button>
        <button type="button" onClick={() => choose("B")}>{question.b}</button>
        {question.c && <button type="button" onClick={() => choose("C")}>{question.c}</button>}
      </div>
      {step > 0 && <button type="button" className="ct-text-button" onClick={() => setPicked(picked.slice(0, -1))}>이전 질문</button>}
    </section>
  );
}
