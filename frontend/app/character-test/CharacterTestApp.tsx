"use client";

import { useState, useSyncExternalStore } from "react";
import { fetchResult, type CharacterResult } from "./api";
import { CardPanel } from "./CardPanel";
import type { Sport } from "./data";
import { InviteSection } from "./InviteSection";
import { MyGroups } from "./MyGroups";
import { getSavedAnswers, saveAnswers } from "./localStore";
import { QuizFlow } from "./QuizFlow";
import { RelationshipMap } from "./RelationshipMap";
import { ResultPanel } from "./ResultPanel";

const EMPTY_LEVELS = (["BEST", "GOOD", "NEUTRAL", "BICKER", "APART"] as const).map((level) => ({ level, participants: [] }));
const subscribeNothing = () => () => {};

type Stage = { kind: "intro" } | { kind: "quiz" } | { kind: "loading" } | { kind: "result"; result: CharacterResult; answers: string; sport: Sport };

export function CharacterTestApp() {
  const [stage, setStage] = useState<Stage>({ kind: "intro" });
  const [error, setError] = useState("");
  const hasSaved = useSyncExternalStore(subscribeNothing, () => getSavedAnswers() !== null, () => false);

  const finish = async (answers: string, sport: Sport) => {
    setStage({ kind: "loading" });
    setError("");
    saveAnswers({ answers, sport });
    try {
      setStage({ kind: "result", result: await fetchResult(answers), answers, sport });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "결과를 불러오지 못했습니다.");
      setStage({ kind: "intro" });
    }
  };

  const showSaved = () => {
    const saved = getSavedAnswers();
    if (saved) void finish(saved.answers, saved.sport);
  };

  if (stage.kind === "quiz") return <QuizFlow onComplete={(answers, sport) => void finish(answers, sport)} />;
  if (stage.kind === "loading") return <section className="ct-panel" role="status"><p>결과를 계산하는 중이에요.</p></section>;
  if (stage.kind === "result") {
    return (
      <>
        <ResultPanel result={stage.result} sport={stage.sport} answers={stage.answers} />
        <CardPanel result={stage.result} sport={stage.sport} />
        <section className="ct-panel" aria-labelledby="ct-map-title">
          <h2 id="ct-map-title">내 관계 지도</h2>
          <p className="ct-lead">아직 다 빈자리예요. 링크를 보내면 사람들이 들어와요.</p>
          <RelationshipMap levels={EMPTY_LEVELS} me={{ character: stage.result.character }} hideNames={false} />
          <InviteSection answers={stage.answers} sport={stage.sport} />
        </section>
        <button type="button" className="ct-text-button" onClick={() => setStage({ kind: "quiz" })}>처음부터 다시 하기</button>
      </>
    );
  }
  return (
    <>
    <section className="ct-panel ct-intro">
      <p className="ct-lead">스키장 상황 15개를 고르면 나의 캐릭터 카드가 나와요. 링크를 보내면 같이 타는 사람들과 어떤 사이인지 지도로 볼 수 있어요.</p>
      <p className="ct-fineprint">가입 없이 할 수 있어요. 재미로 보는 테스트이고, 모임에 들어가기 전까지 답변을 저장하지 않아요.</p>
      {error && <p className="ct-error" role="alert">{error}</p>}
      <button type="button" className="ct-primary" onClick={() => setStage({ kind: "quiz" })}>테스트 시작</button>
      {hasSaved && <button type="button" className="ct-text-button" onClick={showSaved}>지난 결과 다시 보기</button>}
    </section>
    <MyGroups />
    </>
  );
}
