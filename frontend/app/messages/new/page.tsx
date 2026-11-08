"use client";

import Link from "next/link";
import { Suspense, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Footer, TopNav } from "../../components/SiteChrome";
import { MessageComposeForm } from "../../components/MessageComposeForm";
import { MessageRecipientSearch } from "../../components/MessageRecipientSearch";
import { notifyMessagesUpdated } from "../../lib/messageApi";
import styles from "../../components/messages.module.css";

// 주소 값은 화면 표시용이다. 받는 회원과 판매글의 실제 검증은 서버가 보낼 때 다시 한다.
const MAX_PARAM_LENGTH = 100;
const clip = (value: string | null) => (value ?? "").slice(0, MAX_PARAM_LENGTH);

function ComposeMessage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const receiverPublicId = clip(searchParams.get("to"));
  const receiverNickname = clip(searchParams.get("name")) || "회원";
  const listingPublicId = clip(searchParams.get("listing"));
  const listingTitle = clip(searchParams.get("listingTitle"));

  const redirectToLogin = useCallback(() => {
    const returnUrl = `/messages/new?${searchParams.toString()}`;
    router.replace(`/login?returnUrl=${encodeURIComponent(returnUrl)}`);
  }, [router, searchParams]);

  if (!receiverPublicId) {
    return <MessageRecipientSearch onUnauthorized={redirectToLogin} />;
  }

  return (
    <div className={styles.card}>
      <MessageComposeForm
        receiver={{ publicId: receiverPublicId, nickname: receiverNickname }}
        listing={listingPublicId ? { publicId: listingPublicId, title: listingTitle || "판매글" } : null}
        // 주소로 바로 들어와 이전 화면이 없으면 사이트 밖으로 나가지 않게 쪽지함으로 보낸다.
        onCancel={() => (window.history.length > 1 ? router.back() : router.push("/messages"))}
        onUnauthorized={redirectToLogin}
        onSent={() => {
          notifyMessagesUpdated();
          router.replace("/messages?box=sent");
        }}
      />
    </div>
  );
}

export default function NewMessagePage() {
  return (
    <div className="community-page">
      <TopNav active="profile" />
      <main className={`community-container ${styles.page}`}>
        <header className={styles.heading}>
          <div>
            <h1>쪽지 보내기</h1>
            <p>상대를 배려해 주세요. 원치 않는 회원은 닉네임 메뉴에서 차단할 수 있습니다.</p>
          </div>
          <Link href="/messages" className={styles.button}>쪽지함</Link>
        </header>
        <Suspense fallback={<div className={styles.state} aria-busy="true">불러오는 중입니다.</div>}>
          <ComposeMessage />
        </Suspense>
      </main>
      <Footer />
    </div>
  );
}
