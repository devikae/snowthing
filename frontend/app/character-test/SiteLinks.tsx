"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchViewer } from "../lib/viewer";

const LINKS = [
  { href: "/resort-reports", title: "오늘의 설질", description: "리조트에 다녀온 사람들이 남긴 오늘 설질" },
  { href: "/carpool", title: "카풀과 동행", description: "같은 날 같은 리조트로 가는 사람 찾기" },
  { href: "/posts", title: "게시판", description: "장비 질문, 리조트 맛집, 시즌방 모집" },
];

// 결과를 다 본 뒤에 이어서 볼 곳. 테스트 흐름 맨 아래에 두어 카드와 지도를 가리지 않는다.
export function SiteLinks() {
  const [guest, setGuest] = useState(false);

  useEffect(() => {
    let active = true;
    void fetchViewer().then((viewer) => { if (active) setGuest(viewer === null); });
    return () => { active = false; };
  }, []);

  return (
    <section className="ct-panel" aria-labelledby="ct-site-links-title">
      <h2 id="ct-site-links-title">눈팅에서 더 보기</h2>
      <ul className="ct-my-groups">
        {LINKS.map((link) => (
          <li key={link.href}>
            <Link href={link.href}>
              <strong>{link.title}</strong>
              <span>{link.description}</span>
            </Link>
          </li>
        ))}
        {guest && (
          <li>
            <Link href="/signup?returnUrl=%2Fcharacter-test">
              <strong>회원가입</strong>
              <span>카풀 모집과 중고장터는 회원만 쓸 수 있어요</span>
            </Link>
          </li>
        )}
      </ul>
    </section>
  );
}
