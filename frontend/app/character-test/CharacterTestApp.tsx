"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { fetchResult, type CharacterResult } from "./api";
import { CardPanel } from "./CardPanel";
import { characterImage, type JobCode, type Sport } from "./data";
import { InviteSection } from "./InviteSection";
import { ClubLinkMaker } from "./ClubLinkMaker";
import { MyGroups } from "./MyGroups";
import { clearFragment, readResultFragment } from "./links";
import { getSavedAnswers, saveAnswers } from "./localStore";
import { QuizFlow, savedProgressCount } from "./QuizFlow";
import { RelationshipMap } from "./RelationshipMap";
import { SiteLinks } from "./SiteLinks";

// 캐릭터 여덟을 모두 같은 크기로 세운다.
const INTRO_CAST: JobCode[] = ["CAPTAIN", "RESORT_INSIDER", "HYPE", "MOOD_MAKER", "GRINDER", "HOT_PACK", "GEAR_GEEK", "RESORT_OUTSIDER"];

const EMPTY_LEVELS = (["BEST", "GOOD", "NEUTRAL", "BICKER", "APART"] as const).map((level) => ({ level, participants: [] }));
const subscribeNothing = () => () => {};

type Stage = { kind: "intro" } | { kind: "quiz"; resume?: boolean } | { kind: "loading" } | { kind: "result"; result: CharacterResult; answers: string; sport: Sport };

export function CharacterTestApp() {
  const [stage, setStage] = useState<Stage>({ kind: "intro" });
  const [error, setError] = useState("");
  const hasSaved = useSyncExternalStore(subscribeNothing, () => getSavedAnswers() !== null, () => false);
  const progressCount = useSyncExternalStore(subscribeNothing, savedProgressCount, () => 0);

  const finish = async (answers: string, sport: Sport) => {
    setStage({ kind: "loading" });
    setError("");
    saveAnswers({ answers, sport });
    try {
      setStage({ kind: "result", result: await fetchResult(answers), answers, sport });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "결과를 불러오지 못했어요. 다시 시도해 주세요.");
      setStage({ kind: "intro" });
    }
  };

  // 카톡 안 브라우저에서 "다른 브라우저로 열기"로 넘어오면 주소에 실린 답으로 결과를 바로 보여 준다.
  useEffect(() => {
    const restored = readResultFragment();
    if (!restored) return;
    // 렌더 중에 상태를 바꾸지 않게 다음 틱에 시작한다. 개발 모드의 effect 두 번 실행에도 한 번만 돌게 주소는 타이머 안에서 지운다.
    const timer = setTimeout(() => {
      clearFragment();
      void finish(restored.answers, restored.sport);
    }, 0);
    return () => clearTimeout(timer);
  }, []);

  const showSaved = () => {
    const saved = getSavedAnswers();
    if (saved) void finish(saved.answers, saved.sport);
  };

  if (stage.kind === "quiz") return <QuizFlow resume={stage.resume} onComplete={(answers, sport) => void finish(answers, sport)} />;
  if (stage.kind === "loading") return <section className="ct-panel" role="status"><p>결과를 계산하는 중이에요.</p></section>;
  if (stage.kind === "result") {
    return (
      <>
        <CardPanel result={stage.result} sport={stage.sport} answers={stage.answers} />
        <section className="ct-panel" aria-labelledby="ct-map-title">
          <h2 id="ct-map-title">내 관계 지도</h2>
          <p className="ct-lead">아직 다 빈자리예요. 링크를 보내면 사람들이 들어와요.</p>
          <RelationshipMap levels={EMPTY_LEVELS} me={{ character: stage.result.character }} hideNames={false} />
          <InviteSection answers={stage.answers} sport={stage.sport} />
        </section>
        <SiteLinks />
        <button type="button" className="ct-text-button" onClick={() => setStage({ kind: "quiz" })}>처음부터 다시 하기</button>
      </>
    );
  }
  return (
    <>
    <section className="ct-panel ct-intro">
      <div className="ct-intro-cast" aria-hidden="true">
        {INTRO_CAST.map((job) => (
          // eslint-disable-next-line @next/next/no-img-element -- 40칸 픽셀 그림이라 최적화 없이 그대로 키워 보여 준다.
          <img key={job} src={characterImage(job)} width={80} height={80} alt="" />
        ))}
      </div>
      <div className="ct-intro-copy">
        <h2>나는 스키장에서 어떤 캐릭터일까?</h2>
        <p className="ct-lead">스키장에서 겪는 상황 15개에 답하면 나의 캐릭터 카드가 나와요. 링크를 보내면 같이 타는 사람들과 어떤 사이인지 지도로 볼 수 있어요.</p>
      </div>
      <ul className="ct-intro-facts">
        <li><strong>15개</strong><span>질문</span></li>
        <li><strong>1분</strong><span>걸려요</span></li>
        <li><strong>가입 없이</strong><span>바로 시작</span></li>
      </ul>
      {error && <p className="ct-error" role="alert">{error}</p>}
      <div className="ct-intro-actions">
        {progressCount > 0 && <button type="button" className="ct-primary" onClick={() => setStage({ kind: "quiz", resume: true })}>이어서 풀기 (15개 중 {progressCount}개 답함)</button>}
        <button type="button" className={progressCount > 0 ? undefined : "ct-primary"} onClick={() => setStage({ kind: "quiz" })}>{progressCount > 0 ? "처음부터 시작" : "테스트 시작"}</button>
        {hasSaved && <button type="button" className="ct-text-button" onClick={showSaved}>지난 결과 다시 보기</button>}
      </div>
      <p className="ct-fineprint">재미로 보는 테스트예요. 모임에 들어가기 전까지 답변을 서버에 저장하지 않아요.</p>
    </section>
    <MyGroups />
    <ClubLinkMaker />
    </>
  );
}
