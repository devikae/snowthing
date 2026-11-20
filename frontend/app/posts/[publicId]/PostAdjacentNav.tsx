"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { API_ENDPOINTS } from "../../lib/api";

type AdjacentItem = { publicId: string; title: string } | null;
type Adjacent = { previous: AdjacentItem; next: AdjacentItem };

/** 같은 게시판에서 바로 앞(이전글), 바로 뒤(다음글) 글로 가는 링크. 둘 다 없으면 그리지 않는다. */
export function PostAdjacentNav({ publicId }: { publicId: string }) {
  const [adjacent, setAdjacent] = useState<{ id: string; data: Adjacent } | null>(null);

  useEffect(() => {
    let alive = true;
    fetch(API_ENDPOINTS.posts.adjacent(publicId), { credentials: "include" })
      .then((response) => (response.ok ? (response.json() as Promise<Adjacent>) : null))
      .catch(() => null)
      .then((data) => {
        if (alive && data) setAdjacent({ id: publicId, data });
      });
    return () => {
      alive = false;
    };
  }, [publicId]);

  const data = adjacent?.id === publicId ? adjacent.data : null;
  if (!data || (!data.previous && !data.next)) return null;

  // 왼쪽은 이전글(더 오래된 글), 오른쪽은 다음글(더 최근 글). 없는 쪽은 빈 칸으로 두어 반대쪽 버튼이 제자리에 있게 한다.
  const side = (kind: "prev" | "next", item: AdjacentItem) =>
    item ? (
      <Link href={`/posts/${item.publicId}`} className={`post-adjacent-button ${kind}`}>
        <span className="post-adjacent-label">
          {kind === "prev" && <span className="material-symbols-outlined" aria-hidden="true">arrow_back</span>}
          {kind === "prev" ? "이전글" : "다음글"}
          {kind === "next" && <span className="material-symbols-outlined" aria-hidden="true">arrow_forward</span>}
        </span>
        <strong>{item.title}</strong>
      </Link>
    ) : (
      <span className="post-adjacent-button empty" aria-hidden="true" />
    );

  return (
    <nav className="post-adjacent" aria-label="이전글, 다음글">
      {side("prev", data.previous)}
      {side("next", data.next)}
    </nav>
  );
}
