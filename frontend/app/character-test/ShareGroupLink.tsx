"use client";

import { useState } from "react";
import { groupUrl, inviteText } from "./links";
import KakaoShareButton from "../components/KakaoShareButton";
import { shareLink } from "./shareFile";
import type { CharacterLabel, Sport } from "./data";

export const SEND_LINK_LABEL = "모임 링크도 같이 보내기";

// 카드는 사진만 전달되어 받은 사람이 눌러서 들어올 곳이 없다. 이미 모임이 있으면 링크를 바로 보낸다.
// 카톡 공유 그림은 1200x630 가로형이다. 크기를 넘기지 않으면 카카오가 가운데만 정사각형에 가깝게 잘라 보여 준다.
// 보낸 사람 유형이 있으면 그 유형을 가운데 둔 그림(scripts/generate-character-test-images.py --group-share)을, 없으면 공통 그림을 쓴다.
function shareImage(sender?: { character: CharacterLabel; sport: Sport }) {
  if (!sender) return "/character-test/og-group.png";
  return `/character-test/group-${sender.sport}-${sender.character.job}-${sender.character.planStyle}.png`;
}

export function ShareGroupLink({ groupPublicId, groupName, sender }: { groupPublicId: string; groupName: string; sender?: { character: CharacterLabel; sport: Sport } }) {
  const [notice, setNotice] = useState("");
  const [fallbackUrl, setFallbackUrl] = useState("");

  const send = async () => {
    const url = groupUrl(groupPublicId);
    setFallbackUrl("");
    const outcome = await shareLink(url, groupName, inviteText(groupName));
    if (outcome === "failed") setFallbackUrl(url);
    setNotice(outcome === "shared" ? "공유 창을 열었어요." : outcome === "copied" ? "모임 링크를 복사했어요." : outcome === "cancelled" ? "공유를 취소했어요." : "아래 링크를 선택해서 복사해 주세요.");
  };

  return (
    <>
      <button type="button" onClick={() => void send()}>{SEND_LINK_LABEL}</button>
      {/* 모임 주소만 보낸다. 내 복귀 토큰(#t=)은 절대 싣지 않는다. */}
      <KakaoShareButton
        title={groupName}
        description={inviteText(groupName)}
        url={`/character-test/g/${encodeURIComponent(groupPublicId)}`}
        imageUrl={shareImage(sender)}
        imageWidth={1200}
        imageHeight={630}
        buttonTitle="모임 들어가기"
        label="카카오톡으로 모임 링크 보내기"
        onFallback={send}
      />
      {notice && <p className="ct-notice" role="status">{notice}</p>}
      {fallbackUrl && <label className="ct-fallback-link">링크<input readOnly value={fallbackUrl} onFocus={(event) => event.target.select()} /></label>}
    </>
  );
}
