"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { fetchMap, fetchPreview, fetchResult, joinGroup, leaveGroup, type CharacterMap, type CharacterResult, type GroupPreview } from "../../api";
import { CardPanel } from "../../CardPanel";
import { characterName, JOBS, LEVELS, SPORT_LABEL, type Sport } from "../../data";
import { INVITE_LABEL, InviteSection } from "../../InviteSection";
import { clearFragment, groupUrl, inviteText, readFragmentToken, restoreUrl } from "../../links";
import { getMembership, getSavedAnswers, removeMembership, saveAnswers, saveMembership } from "../../localStore";
import { renderMapImage } from "../../mapImage";
import { NicknameForm } from "../../NicknameForm";
import { QuizFlow } from "../../QuizFlow";
import { RelationshipMap } from "../../RelationshipMap";
import { ResultPanel } from "../../ResultPanel";
import { shareLink, shareOrSaveImage } from "../../shareFile";

type View =
  | { kind: "loading" }
  | { kind: "missing" }
  | { kind: "error"; message: string }
  | { kind: "preview"; preview: GroupPreview; notice: string; hasSaved: boolean }
  | { kind: "quiz"; preview: GroupPreview }
  | { kind: "result"; preview: GroupPreview; result: CharacterResult; answers: string; sport: Sport }
  | { kind: "map"; map: CharacterMap; token: string };

// 링크 하나로 개인과 동호회를 모두 처리한다. 토큰이 있으면 자기 지도, 없으면 테스트를 거쳐 들어온다.
export function GroupApp({ publicId }: { publicId: string }) {
  const [view, setView] = useState<View>({ kind: "loading" });

  const showPreview = useCallback(async (notice = "") => {
    const preview = await fetchPreview(publicId);
    setView(preview ? { kind: "preview", preview, notice, hasSaved: getSavedAnswers() !== null } : { kind: "missing" });
  }, [publicId]);

  const load = useCallback(async () => {
    try {
      const fragmentToken = readFragmentToken();
      if (fragmentToken) clearFragment();
      const stored = getMembership(publicId);
      const token = fragmentToken ?? stored?.token ?? null;
      if (!token) {
        await showPreview();
        return;
      }
      const map = await fetchMap(publicId, token);
      if (!map) {
        // 모임이 지워졌거나 복귀 링크가 틀렸다. 저장한 토큰을 지우고 처음 온 사람처럼 보여 준다.
        if (stored) removeMembership(publicId);
        await showPreview(stored || fragmentToken ? "이 지도에 더 이상 들어갈 수 없어요. 다시 테스트하고 들어올 수 있어요." : "");
        return;
      }
      saveMembership(publicId, { token, nickname: map.me.nickname, groupName: map.groupName });
      setView({ kind: "map", map, token });
    } catch (reason) {
      setView({ kind: "error", message: reason instanceof Error ? reason.message : "불러오지 못했습니다." });
    }
  }, [publicId, showPreview]);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const finishQuiz = async (preview: GroupPreview, answers: string, sport: Sport) => {
    setView({ kind: "loading" });
    saveAnswers({ answers, sport });
    try {
      setView({ kind: "result", preview, result: await fetchResult(answers), answers, sport });
    } catch (reason) {
      setView({ kind: "error", message: reason instanceof Error ? reason.message : "결과를 불러오지 못했습니다." });
    }
  };

  if (view.kind === "loading") return <section className="ct-panel" role="status"><p>불러오는 중이에요.</p></section>;
  if (view.kind === "missing") {
    return <section className="ct-panel"><h2>모임을 찾을 수 없어요</h2><p className="ct-lead">링크가 잘못됐거나 오래되어 지워진 모임이에요. 테스트는 언제든 새로 할 수 있어요.</p><Link className="ct-primary" href="/character-test">테스트 하러 가기</Link></section>;
  }
  if (view.kind === "error") {
    return <section className="ct-panel"><h2>문제가 생겼어요</h2><p className="ct-error" role="alert">{view.message}</p><button type="button" className="ct-primary" onClick={() => { setView({ kind: "loading" }); void load(); }}>다시 시도</button></section>;
  }
  if (view.kind === "preview") {
    const { preview, notice, hasSaved } = view;
    const saved = getSavedAnswers();
    return (
      <section className="ct-panel ct-intro">
        <p className="ct-eyebrow">초대받았어요</p>
        <h2>{preview.name}</h2>
        <p className="ct-lead">지금 {preview.participantCount}명이 있어요. 스키장 상황 15개를 고르고 닉네임을 넣으면 나를 가운데 둔 관계 지도가 나와요.</p>
        {notice && <p className="ct-error" role="status">{notice}</p>}
        <p className="ct-fineprint">가입 없이 할 수 있어요. 재미로 보는 테스트이고, 닉네임을 넣기 전까지 답변을 저장하지 않아요.</p>
        <button type="button" className="ct-primary" onClick={() => setView({ kind: "quiz", preview })}>테스트 시작</button>
        {hasSaved && saved && <button type="button" className="ct-text-button" onClick={() => void finishQuiz(preview, saved.answers, saved.sport)}>지난 답변으로 바로 들어가기</button>}
      </section>
    );
  }
  if (view.kind === "quiz") return <QuizFlow onComplete={(answers, sport) => void finishQuiz(view.preview, answers, sport)} />;
  if (view.kind === "result") {
    const { preview, result, answers, sport } = view;
    const join = async (nickname: string) => {
      const joined = await joinGroup(publicId, { nickname, sport, answers });
      saveMembership(publicId, { token: joined.accessToken, nickname, groupName: joined.groupName });
      setView({ kind: "loading" });
      await load();
    };
    return (
      <>
        <ResultPanel result={result} sport={sport} answers={answers} />
        <section className="ct-panel" aria-labelledby="ct-join-title">
          <h2 id="ct-join-title">{preview.name}에 들어가기</h2>
          <p className="ct-lead">닉네임만 넣으면 이 모임 사람들과 어떤 사이인지 지도로 보여 줘요.</p>
          <NicknameForm submitLabel="모임에 들어가기" onSubmit={join} />
        </section>
        <CardPanel result={result} sport={sport} />
      </>
    );
  }
  return <MapView map={view.map} token={view.token} onReload={load} onLeft={() => showPreview("모임에서 나왔어요. 내 닉네임과 답변을 지웠어요.")} />;
}

function MapView({ map, token, onReload, onLeft }: { map: CharacterMap; token: string; onReload: () => Promise<void>; onLeft: () => Promise<void> }) {
  const [hideNames, setHideNames] = useState(false);
  const [notice, setNotice] = useState("");
  const [fallbackUrl, setFallbackUrl] = useState("");
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [busy, setBusy] = useState(false);
  const { me, groupPublicId, groupName } = map;
  const character = me.result.character;

  const shareGroup = async () => {
    const url = groupUrl(groupPublicId);
    setFallbackUrl("");
    const outcome = await shareLink(url, groupName, inviteText(groupName));
    if (outcome === "failed") setFallbackUrl(url);
    setNotice(outcome === "shared" ? "공유 창을 열었어요." : outcome === "copied" ? "모임 링크를 복사했어요." : outcome === "cancelled" ? "공유를 취소했어요." : "아래 링크를 선택해서 복사해 주세요.");
  };

  const copyRestore = async () => {
    const url = restoreUrl(groupPublicId, token);
    setFallbackUrl("");
    try {
      await navigator.clipboard.writeText(url);
      setNotice("내 복귀 링크를 복사했어요. 이 링크를 가진 사람은 내 지도로 들어올 수 있으니 혼자만 보관하세요.");
    } catch {
      setFallbackUrl(url);
      setNotice("아래 링크를 선택해서 복사해 주세요. 이 링크를 가진 사람은 내 지도로 들어올 수 있으니 혼자만 보관하세요.");
    }
  };

  const saveMap = async () => {
    setBusy(true);
    try {
      const blob = await renderMapImage({
        title: hideNames ? "우리 모임 관계 지도" : `${groupName} 관계 지도`,
        levels: map.levels,
        me: { character },
        hideNames,
      });
      const outcome = await shareOrSaveImage(blob, "snowthing-character-map.png", "우리 모임 관계 지도");
      setNotice(outcome === "shared" ? "공유 창을 열었어요." : outcome === "saved" ? "지도 이미지를 저장했어요." : "공유를 취소했어요.");
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "지도 이미지를 만들지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const leave = async () => {
    setBusy(true);
    try {
      await leaveGroup(token);
      removeMembership(groupPublicId);
      await onLeft();
    } catch (reason) {
      setNotice(reason instanceof Error ? reason.message : "나가지 못했습니다.");
      setBusy(false);
    }
  };

  return (
    <>
      <section className="ct-panel" aria-labelledby="ct-group-title">
        <div className="ct-group-head">
          <div>
            <p className="ct-eyebrow">{characterName(character)}</p>
            <h2 id="ct-group-title">{groupName}</h2>
          </div>
          <p className="ct-count">{map.participantCount} / {map.maxParticipants}명</p>
        </div>
        <RelationshipMap levels={map.levels} me={{ character }} hideNames={hideNames} />
        <label className="ct-check"><input type="checkbox" checked={hideNames} onChange={(event) => setHideNames(event.target.checked)} /> 닉네임 가리기</label>
        <div className="ct-actions">
          <button type="button" onClick={() => void saveMap()} disabled={busy}>지도 이미지 저장</button>
          <button type="button" onClick={() => void onReload()} disabled={busy}>지도 새로고침</button>
        </div>
        <button type="button" className="ct-primary" onClick={() => void shareGroup()}>{map.owner ? INVITE_LABEL : "이 모임 링크 보내기"}</button>
        <div className="ct-new-group">
          <p className="ct-lead">다른 사람들이랑도 해 보고 싶다면 새 모임을 만들어 보세요. 테스트를 다시 하지 않아도 돼요.</p>
          <InviteSection answers={me.answers} sport={me.sport} initialNickname={me.nickname} label="새 모임 만들기" primary={false} />
        </div>
        {notice && <p className="ct-notice" role="status">{notice}</p>}
        {fallbackUrl && <label className="ct-fallback-link">링크<input readOnly value={fallbackUrl} onFocus={(event) => event.target.select()} /></label>}
      </section>

      <section className="ct-panel" aria-labelledby="ct-roster-title">
        <h2 id="ct-roster-title">전체 명단</h2>
        <p className="ct-lead">단계별로 점수가 높은 순서예요.</p>
        {LEVELS.map((level) => {
          const people = map.levels.find((row) => row.level === level.code)?.participants ?? [];
          return (
            <details key={level.code} className="ct-roster" open={people.length > 0 && people.length <= 5}>
              <summary>{level.label} {people.length}명</summary>
              {people.length === 0 ? <p className="ct-fineprint">아직 빈자리예요.</p> : (
                <ul>
                  {people.map((person) => (
                    <li key={person.participantPublicId}>
                      <span><strong>{hideNames ? "닉네임 숨김" : person.nickname}</strong> {characterName(person.character)} ({JOBS[person.character.job].name}, {SPORT_LABEL[person.sport]}) {person.score}점</span>
                    </li>
                  ))}
                </ul>
              )}
            </details>
          );
        })}
      </section>

      <CardPanel result={me.result} sport={me.sport} />

      <section className="ct-panel ct-account" aria-labelledby="ct-account-title">
        <h2 id="ct-account-title">내 정보 관리</h2>
        <p className="ct-lead">{me.nickname}(으)로 들어와 있어요. 이 브라우저에 접속 정보가 저장되어 있어요.</p>
        <div className="ct-actions">
          <button type="button" onClick={() => void copyRestore()}>내 복귀 링크 복사</button>
          {confirmLeave
            ? <span className="ct-inline-actions"><button type="button" onClick={() => void leave()} disabled={busy}>나가기 확인</button><button type="button" onClick={() => setConfirmLeave(false)}>취소</button></span>
            : <button type="button" onClick={() => setConfirmLeave(true)}>모임 나가기</button>}
        </div>
        <p className="ct-fineprint">나가면 내 닉네임과 답변이 바로 지워져요.</p>
      </section>
    </>
  );
}
