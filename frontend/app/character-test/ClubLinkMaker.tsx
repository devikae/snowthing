"use client";

import { useEffect, useState, type FormEvent } from "react";
import { fetchViewer, isAdminViewer } from "../lib/viewer";
import { createClubGroup, type GroupPreview } from "./api";
import { groupUrl } from "./links";
import { ShareGroupLink } from "./ShareGroupLink";

const GROUP_NAME_PATTERN = /^[가-힣a-zA-Z0-9 ]{2,20}$/;

/** 운영자 전용. 내가 참여하지 않고 동호회 오픈채팅에 뿌릴 모임 링크만 만든다. 관리자가 아니면 그리지 않는다. */
export function ClubLinkMaker() {
  const [admin, setAdmin] = useState(false);
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const [created, setCreated] = useState<GroupPreview[]>([]);

  useEffect(() => {
    let alive = true;
    fetchViewer()
      .then((viewer) => {
        if (alive) setAdmin(isAdminViewer(viewer));
      })
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, []);

  if (!admin) return null;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const clean = name.trim().replace(/\s+/g, " ");
    if (!GROUP_NAME_PATTERN.test(clean)) {
      setError("모임 이름은 2자 이상 20자 이하의 한글, 영문, 숫자, 띄어쓰기로 입력해 주세요.");
      return;
    }
    setError("");
    setPending(true);
    try {
      const group = await createClubGroup(clean);
      setCreated((current) => [group, ...current]);
      setName("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "다시 시도해 주세요.");
    } finally {
      setPending(false);
    }
  };

  return (
    <section className="ct-panel" aria-labelledby="ct-club-title">
      <p className="ct-eyebrow">운영자만 보여요</p>
      <h2 id="ct-club-title">동호회 링크 만들기</h2>
      <p className="ct-lead">내가 참여하지 않고 링크만 만들어요. 오픈채팅에 올리면 들어온 사람이 이름을 넣고 테스트를 마치는 순간 이 모임에 들어가요.</p>
      <form className="ct-nickname" onSubmit={(event) => void submit(event)} noValidate>
        <label htmlFor="ct-club-name">모임 이름</label>
        <input id="ct-club-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={20} placeholder="예: 설원 보드 동호회" autoComplete="off" aria-invalid={Boolean(error)} />
        {error && <p className="ct-error" role="alert">{error}</p>}
        <button type="submit" className="ct-primary" disabled={pending}>{pending ? "만드는 중" : "링크 만들기"}</button>
      </form>
      {created.map((group) => (
        <div key={group.publicId} className="ct-club-created">
          <strong>{group.name}</strong>
          <label className="ct-fallback-link">링크<input readOnly value={groupUrl(group.publicId)} onFocus={(event) => event.target.select()} /></label>
          <ShareGroupLink groupPublicId={group.publicId} groupName={group.name} />
        </div>
      ))}
    </section>
  );
}
