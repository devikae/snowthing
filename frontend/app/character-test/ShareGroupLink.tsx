"use client";

import { useState } from "react";
import { groupUrl, inviteText } from "./links";
import KakaoShareButton from "../components/KakaoShareButton";
import type { ReactNode } from "react";
import type { CharacterLabel, Sport } from "./data";

export const KAKAO_INVITE_LABEL = "카카오톡으로 모임에 초대하기";

// 카드는 사진만 전달되어 받은 사람이 눌러서 들어올 곳이 없다. 이미 모임이 있으면 링크를 바로 보낸다.
// 카톡 공유 그림 크기를 같이 넘겨야 카카오가 가운데만 잘라 보여 주지 않는다.
// 보낸 사람 유형이 있으면 그 유형을 가운데 둔 정사각형 그림(scripts/generate-character-test-images.py --group-share)을 쓴다.
// 말풍선 폭은 카카오가 정하므로 정사각형이 가로 그림보다 두 배쯤 크게 보인다. 유형이 없으면(운영자가 만든 링크) 공통 가로 그림이다.
function shareImage(sender?: { character: CharacterLabel; sport: Sport }) {
  if (!sender) return { url: "/character-test/og-group.png", width: 1200, height: 630 };
  return { url: `/character-test/group-share-${sender.sport}-${sender.character.job}-${sender.character.planStyle}.png`, width: 800, height: 800 };
}

// 모임에 사람을 부르는 버튼은 카톡 초대 하나를 크게, 링크 복사를 작게 둔다. extra는 링크 복사 옆에 같이 놓을 작은 버튼이다.
export function ShareGroupLink({ groupPublicId, groupName, sender, extra }: { groupPublicId: string; groupName: string; sender?: { character: CharacterLabel; sport: Sport }; extra?: ReactNode }) {
  const [notice, setNotice] = useState("");
  const image = shareImage(sender);
  const [fallbackUrl, setFallbackUrl] = useState("");

  // 링크 복사는 공유 창을 띄우지 않고 복사만 한다. 복사가 막힌 브라우저는 링크 칸을 보여 준다.
  const copy = async () => {
    const url = groupUrl(groupPublicId);
    setFallbackUrl("");
    try {
      await navigator.clipboard.writeText(url);
      setNotice("모임 링크를 복사했어요. 단톡방에 붙여 넣으면 돼요.");
    } catch {
      setFallbackUrl(url);
      setNotice("아래 링크를 선택해서 복사해 주세요.");
    }
  };

  return (
    <>
      {/* 모임 주소만 보낸다. 내 복귀 토큰(#t=)은 절대 싣지 않는다. */}
      <KakaoShareButton
        title={groupName}
        description={inviteText(groupName)}
        url={`/character-test/g/${encodeURIComponent(groupPublicId)}`}
        imageUrl={image.url}
        imageWidth={image.width}
        imageHeight={image.height}
        buttonTitle="모임 들어가기"
        label={KAKAO_INVITE_LABEL}
        onFallback={copy}
      />
      <div className="ct-share-row">
        <button type="button" onClick={() => void copy()}>링크 복사</button>
        {extra}
      </div>
      {notice && <p className="ct-notice" role="status">{notice}</p>}
      {fallbackUrl && <label className="ct-fallback-link">링크<input readOnly value={fallbackUrl} onFocus={(event) => event.target.select()} /></label>}
    </>
  );
}
