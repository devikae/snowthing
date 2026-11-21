"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchViewer } from "../lib/viewer";
import styles from "./resorts.module.css";

/**
 * 비로그인 방문자에게만 보이는 가입 안내. 서버 HTML에는 넣지 않고, 로그인 여부를 확인한 뒤에 그린다.
 * 로그인한 회원에게 잠깐 보였다 사라지지 않게 하기 위해서다.
 */
export function JoinPrompt() {
  const [guest, setGuest] = useState(false);

  useEffect(() => {
    let alive = true;
    // 헤더가 이미 부른 내 정보 요청을 같이 쓴다(viewer.ts가 한 번만 부른다).
    void fetchViewer().then((viewer) => {
      if (alive) setGuest(viewer === null);
    });
    return () => {
      alive = false;
    };
  }, []);

  if (!guest) return null;
  return (
    <aside className={styles.join}>
      <p>
        <strong>설질 제보와 카풀 모집은 회원이 올립니다.</strong>
        <span>가입하면 오늘 슬로프 상태를 남기고, 같은 리조트로 가는 동행을 구할 수 있어요.</span>
      </p>
      <div>
        <Link href="/signup" className="snow-btn-primary">회원가입</Link>
        <Link href="/login" className="snow-btn-secondary">로그인</Link>
      </div>
    </aside>
  );
}
