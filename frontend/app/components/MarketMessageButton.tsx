"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { composeMessageHref, fetchViewerPublicId } from "../lib/messageApi";
import styles from "./messages.module.css";

interface MarketMessageButtonProps {
  seller: { publicId: string; nickname: string };
  listing: { publicId: string; title: string };
}

/** 중고장터 판매글의 "쪽지 보내기". 로그인했고 본인 글이 아닐 때만 보인다. 보낸 쪽지는 판매글을 참조한다. */
export function MarketMessageButton({ seller, listing }: MarketMessageButtonProps) {
  const [viewer, setViewer] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void fetchViewerPublicId().then((value) => {
      if (!cancelled) setViewer(value);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!viewer || viewer === seller.publicId) return null;
  return (
    <Link className={`${styles.buttonPrimary} ${styles.marketMessage}`} href={composeMessageHref(seller, listing)}>
      <span className="material-symbols-outlined" aria-hidden="true">sticky_note_2</span>
      쪽지 보내기
    </Link>
  );
}
