"use client";

import { useState } from "react";
import { QUESTIONS, type Sport } from "./data";
import { clearQuizProgress, getQuizProgress, saveQuizProgress, type Choice } from "./localStore";

function shuffledOrder() {
  const order = QUESTIONS.map((_, index) => index);
  for (let index = order.length - 1; index > 0; index -= 1) {
    const other = Math.floor(Math.random() * (index + 1));
    [order[index], order[other]] = [order[other], order[index]];
  }
  return order;
}

// 시작 화면의 "이어서 풀기" 버튼에 쓴다. 고른 답이 하나도 없으면 0이다.
export function savedProgressCount() {
  return getQuizProgress(QUESTIONS.length)?.picked.length ?? 0;
}

// 답변은 이 브라우저에만 둔다. 푸는 중에는 localStorage에 남겨 다시 열어도 이어서 풀고, 끝나면 지운다.
// 묶음 이름은 보여 주지 않고, 문자열은 질문 번호 순서로 되돌려 넘긴다.
export function QuizFlow({ resume = false, onComplete }: { resume?: boolean; onComplete: (answers: string, sport: Sport) => void }) {
  // 이어 풀기가 아니면 지난 진행을 지우고 새로 섞는다. 이 화면은 버튼을 누른 뒤에만 그려져 서버 렌더와 어긋나지 않는다.
  const [initial] = useState(() => {
    const saved = resume ? getQuizProgress(QUESTIONS.length) : null;
    if (!saved) clearQuizProgress();
    return saved ?? { sport: null, order: shuffledOrder(), picked: [] as Choice[] };
  });
  const [sport, setSportState] = useState<Sport | null>(initial.sport);
  const order = initial.order;
  const [picked, setPickedState] = useState<Choice[]>(initial.picked);

  const setSport = (next: Sport) => {
    setSportState(next);
    saveQuizProgress({ sport: next, order, picked });
  };
  const setPicked = (next: Choice[]) => {
    setPickedState(next);
    if (sport) saveQuizProgress({ sport, order, picked: next });
  };

  if (!sport) {
    return (
      <section className="ct-panel ct-question">
        <p className="ct-eyebrow ct-step-line">시작하기</p>
        <h2 className="ct-step-line">오늘은 뭘 타나요?</h2>
        <p className="ct-fineprint ct-step-line">결과에는 영향을 주지 않고 지도에 표시만 돼요.</p>
        <div className="ct-choices ct-step-line">
          <button type="button" onClick={() => setSport("SKI")}>스키</button>
          <button type="button" onClick={() => setSport("BOARD")}>보드</button>
        </div>
      </section>
    );
  }

  const step = picked.length;
  const question = QUESTIONS[order[step]];
  const choose = (choice: Choice) => {
    const next = [...picked, choice];
    if (next.length < order.length) {
      setPicked(next);
      return;
    }
    clearQuizProgress();
    const answers = Array.from<string>({ length: order.length });
    order.forEach((questionIndex, position) => { answers[questionIndex] = next[position]; });
    onComplete(answers.join(""), sport);
  };

  return (
    <section className="ct-panel ct-question">
      <div className="ct-progress" role="progressbar" aria-valuemin={0} aria-valuemax={order.length} aria-valuenow={step + 1} aria-label={`${order.length}개 중 ${step + 1}번째 질문`}><span style={{ width: `${((step + 1) / order.length) * 100}%` }} /></div>
      {/* 질문이 바뀔 때마다 key로 새로 그려 한 줄씩 나타나는 움직임을 다시 보여 준다 */}
      <p key={`n${step}`} className="ct-eyebrow ct-step-line">{step + 1} / {order.length}</p>
      <h2 key={`q${step}`} className="ct-step-line">{question.situation}</h2>
      <div key={`c${step}`} className="ct-choices ct-step-line">
        <button type="button" onClick={() => choose("A")}>{question.a}</button>
        <button type="button" onClick={() => choose("B")}>{question.b}</button>
        {question.c && <button type="button" onClick={() => choose("C")}>{question.c}</button>}
      </div>
      {step > 0 && <button type="button" className="ct-text-button" onClick={() => setPicked(picked.slice(0, -1))}>이전 질문</button>}
    </section>
  );
}
