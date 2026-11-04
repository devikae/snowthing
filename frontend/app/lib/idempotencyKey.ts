import { useCallback, useRef } from "react";

/** 작성 API가 같은 요청의 재전송을 알아보는 헤더 (IKA-21). */
export const IDEMPOTENCY_HEADER = "Idempotency-Key";

function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // randomUUID는 보안 컨텍스트(HTTPS, localhost)에서만 있다. 그 밖에서는 같은 형식(v4)을 직접 만든다.
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * 작성 폼 한 개가 쓰는 Idempotency-Key.
 *
 * - keyFor(body): 보낼 본문을 받아 키를 돌려준다. 직전 시도와 본문이 같으면(실패 후 재시도) 같은 키를, 본문이 바뀌었으면 새 키를 준다.
 * - reset(): 작성에 성공하면 불러 다음 글이 새 키를 쓰게 한다.
 *
 * 본문이 바뀌면 새 키를 쓰는 이유: 같은 키에 다른 내용을 보내면 서버가 409로 막기 때문에, 고쳐 쓴 글이 409에 걸리지 않게 한다.
 */
export function useIdempotencyKey() {
  const keyRef = useRef<string | null>(null);
  const bodyRef = useRef<string | null>(null);

  const keyFor = useCallback((body: string) => {
    if (keyRef.current === null || bodyRef.current !== body) {
      keyRef.current = newIdempotencyKey();
      bodyRef.current = body;
    }
    return keyRef.current;
  }, []);

  const reset = useCallback(() => {
    keyRef.current = null;
    bodyRef.current = null;
  }, []);

  return { keyFor, reset };
}
