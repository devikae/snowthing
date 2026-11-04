"use client";

import { FormEvent, useRef, useState } from "react";
import { csrfFetch } from "../lib/csrfFetch";
import { MESSAGE_ENDPOINTS, MESSAGE_MAX_LENGTH, readErrorMessage } from "../lib/messageApi";
import styles from "./messages.module.css";

interface MessageComposeFormProps {
  receiver: { publicId: string; nickname: string };
  listing?: { publicId: string; title: string } | null;
  quote?: string | null;
  onSent: () => void;
  onUnauthorized: () => void;
  onCancel?: () => void;
}

/** 쪽지 작성. 보내기는 연타를 막고, 서버가 거부한 이유(차단, 보낼 수 없는 회원 등)는 그대로 보여 준다. */
export function MessageComposeForm({ receiver, listing, quote, onSent, onUnauthorized, onCancel }: MessageComposeFormProps) {
  const [content, setContent] = useState("");
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const sendingRef = useRef(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (sendingRef.current) return;
    const body = content.trim();
    if (!body) {
      setError("쪽지 내용을 입력해 주세요.");
      return;
    }
    sendingRef.current = true;
    setSending(true);
    setError("");
    try {
      const response = await csrfFetch(MESSAGE_ENDPOINTS.send, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ receiverPublicId: receiver.publicId, content: body, marketListingPublicId: listing?.publicId ?? null }),
      });
      if (response.status === 401) {
        onUnauthorized();
        return;
      }
      if (!response.ok) throw new Error(await readErrorMessage(response, "쪽지를 보내지 못했습니다."));
      setContent("");
      onSent();
    } catch (sendError) {
      setError(sendError instanceof Error ? sendError.message : "쪽지를 보내지 못했습니다.");
    } finally {
      sendingRef.current = false;
      setSending(false);
    }
  };

  return (
    <form className={styles.compose} onSubmit={submit}>
      {error && <p className={styles.error} role="alert">{error}</p>}
      <p className={styles.meta}>
        받는 회원 <strong>{receiver.nickname}</strong>
        {listing && <> 판매글 <strong>{listing.title}</strong></>}
      </p>
      {quote && <blockquote className={styles.quote} aria-label="원문">{quote}</blockquote>}
      <label>
        내용
        <textarea
          value={content}
          maxLength={MESSAGE_MAX_LENGTH}
          onChange={(event) => setContent(event.target.value)}
          placeholder="상대에게 보낼 내용을 입력하세요."
          autoFocus
          required
        />
      </label>
      <div className={styles.composeFooter}>
        <span>{content.length}/{MESSAGE_MAX_LENGTH}</span>
        <div className={styles.actions} style={{ marginTop: 0 }}>
          {onCancel && <button type="button" className={styles.button} onClick={onCancel} disabled={sending}>취소</button>}
          <button type="submit" className={styles.buttonPrimary} disabled={sending || !content.trim()}>
            {sending ? "보내는 중" : "보내기"}
          </button>
        </div>
      </div>
    </form>
  );
}
