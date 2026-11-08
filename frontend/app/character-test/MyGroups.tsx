"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getMemberships, type SavedMembership } from "./localStore";

// 가입이 없어서 이 브라우저에 저장된 모임만 보여 준다. 저장 값은 서버에서 그려진 뒤에 읽는다.
export function MyGroups() {
  const [groups, setGroups] = useState<SavedMembership[]>([]);

  useEffect(() => {
    const timer = setTimeout(() => setGroups(getMemberships()), 0);
    return () => clearTimeout(timer);
  }, []);

  if (groups.length === 0) return null;
  return (
    <section className="ct-panel" aria-labelledby="ct-my-groups-title">
      <h2 id="ct-my-groups-title">내 모임</h2>
      <ul className="ct-my-groups">
        {groups.map((group) => (
          <li key={group.groupPublicId}>
            <Link href={`/character-test/g/${group.groupPublicId}`}>
              <strong>{group.groupName ?? "이름 없는 모임"}</strong>
              <span>{group.nickname}(으)로 참여 중</span>
            </Link>
          </li>
        ))}
      </ul>
      <p className="ct-fineprint">이 브라우저에 저장된 모임만 보여요.</p>
    </section>
  );
}
