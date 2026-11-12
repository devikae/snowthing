"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { createGroup } from "./api";
import type { Sport } from "./data";
import { saveMembership } from "./localStore";
import { NicknameForm } from "./NicknameForm";
import { groupUrl, inviteText } from "./links";
import { shareLink } from "./shareFile";

export const INVITE_LABEL = "같이 타는 사람 부르기";

// 처음 누를 때 이름과 모임 이름(선택)을 받는다. 모임이 자동으로 만들어지고 공유 창을 띄운 뒤 지도로 옮겨 간다.
export function InviteSection({ answers, sport, initialNickname, label = INVITE_LABEL, primary = true }: { answers: string; sport: Sport; initialNickname?: string; label?: string; primary?: boolean }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);

  const create = async (nickname: string, groupName?: string) => {
    const created = await createGroup({ nickname, sport, answers, groupName });
    saveMembership(created.groupPublicId, { token: created.accessToken, nickname, groupName: created.groupName });
    await shareLink(groupUrl(created.groupPublicId), created.groupName, inviteText(created.groupName));
    router.push(`/character-test/g/${created.groupPublicId}`);
  };

  if (!open) return <button type="button" className={primary ? "ct-primary" : undefined} onClick={() => setOpen(true)}>{label}</button>;
  return <NicknameForm withGroupName submitLabel="링크 만들고 보내기" initialNickname={initialNickname} onSubmit={create} onCancel={() => setOpen(false)} />;
}
