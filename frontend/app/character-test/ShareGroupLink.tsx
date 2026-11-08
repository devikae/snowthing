"use client";

import { useState } from "react";
import { groupUrl, inviteText } from "./links";
import { shareLink } from "./shareFile";

export const SEND_LINK_LABEL = "모임 링크도 같이 보내기";

// 카드는 사진만 전달되어 받은 사람이 눌러서 들어올 곳이 없다. 이미 모임이 있으면 링크를 바로 보낸다.
export function ShareGroupLink({ groupPublicId, groupName }: { groupPublicId: string; groupName: string }) {
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
      {notice && <p className="ct-notice" role="status">{notice}</p>}
      {fallbackUrl && <label className="ct-fallback-link">링크<input readOnly value={fallbackUrl} onFocus={(event) => event.target.select()} /></label>}
    </>
  );
}
