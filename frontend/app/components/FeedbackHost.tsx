"use client";

import { useEffect, useId, useRef, useSyncExternalStore } from "react";
import { dismissToast, getFeedbackState, settleConfirm, subscribeFeedback, type ConfirmRequest, type ToastItem } from "../lib/feedback";
import styles from "./feedback.module.css";

const ICONS = { success: "check_circle", error: "error", info: "info" } as const;
/** 오류는 읽고 대처할 시간이 필요해 더 오래 둔다. */
const DURATION_MS = { success: 3500, info: 3500, error: 7000 } as const;

const serverState = { toasts: [], confirm: null };

/** 토스트와 확인 창을 그리는 자리. 루트 레이아웃에 한 번만 둔다. 부르는 쪽은 lib/feedback의 toast, confirm을 쓴다. */
export function FeedbackHost() {
  const state = useSyncExternalStore(subscribeFeedback, getFeedbackState, () => serverState);
  const errors = state.toasts.filter((item) => item.kind === "error");
  const others = state.toasts.filter((item) => item.kind !== "error");
  const stackRef = useRef<HTMLDivElement>(null);
  const hasToasts = state.toasts.length > 0;

  // 모바일에서 화면 키보드가 올라오면 보이는 영역이 아래로 밀린다. 그만큼 토스트 위치를 내려 계속 보이게 한다.
  useEffect(() => {
    const viewport = window.visualViewport;
    const stack = stackRef.current;
    if (!hasToasts || !viewport || !stack) return;
    const sync = () => stack.style.setProperty("--viewport-top", `${viewport.offsetTop}px`);
    sync();
    viewport.addEventListener("resize", sync);
    viewport.addEventListener("scroll", sync);
    return () => {
      viewport.removeEventListener("resize", sync);
      viewport.removeEventListener("scroll", sync);
    };
  }, [hasToasts]);
  return (
    <>
      {/* 안내 영역은 처음부터 문서에 있어야 화면 읽기 프로그램이 새 문구를 읽는다. 초점은 옮기지 않는다. */}
      <div ref={stackRef} className={styles.stack}>
        <div aria-live="assertive" aria-relevant="additions">
          {errors.map((item) => <Toast key={item.id} item={item} />)}
        </div>
        <div aria-live="polite" aria-relevant="additions">
          {others.map((item) => <Toast key={item.id} item={item} />)}
        </div>
      </div>
      {state.confirm && <ConfirmDialog request={state.confirm} />}
    </>
  );
}

function Toast({ item }: { item: ToastItem }) {
  const timer = useRef<number | undefined>(undefined);
  const start = () => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => dismissToast(item.id), DURATION_MS[item.kind]);
  };
  const stop = () => window.clearTimeout(timer.current);
  useEffect(() => {
    timer.current = window.setTimeout(() => dismissToast(item.id), DURATION_MS[item.kind]);
    return () => window.clearTimeout(timer.current);
  }, [item.id, item.kind]);

  return (
    // 마우스를 올리거나 닫기 버튼에 초점이 있는 동안은 사라지지 않는다.
    <div className={`${styles.toast} ${styles[item.kind]}`} onMouseEnter={stop} onMouseLeave={start} onFocus={stop} onBlur={start}>
      <span data-nosnippet className={`material-symbols-outlined ${styles.icon}`} aria-hidden="true">{ICONS[item.kind]}</span>
      <p>{item.kind === "error" && <span className="sr-only">오류: </span>}{item.message}</p>
      <button type="button" className={styles.close} onClick={() => dismissToast(item.id)} aria-label="안내 닫기">
        <span data-nosnippet className="material-symbols-outlined" aria-hidden="true">close</span>
      </button>
    </div>
  );
}

function ConfirmDialog({ request }: { request: ConfirmRequest }) {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  // showModal은 뒤 화면을 조작할 수 없게 하고(inert), Esc 처리와 닫은 뒤 초점 복귀를 브라우저가 맡는다.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    dialog.showModal();
    // 위험한 동작은 실수로 Enter를 눌러도 실행되지 않게 취소에 초점을 둔다.
    (request.danger ? cancelRef.current : confirmRef.current)?.focus();
    const overflow = document.documentElement.style.overflow;
    document.documentElement.style.overflow = "hidden";
    return () => {
      document.documentElement.style.overflow = overflow;
      if (dialog.open) dialog.close();
    };
  }, [request.danger]);

  const settle = (ok: boolean) => {
    // close()가 창을 연 버튼으로 초점을 돌려준다. 요소가 지워지기 전에 먼저 부른다.
    dialogRef.current?.close();
    settleConfirm(ok);
  };

  return (
    <dialog
      ref={dialogRef}
      role="alertdialog"
      aria-labelledby={titleId}
      aria-describedby={request.description ? descriptionId : undefined}
      className={styles.dialog}
      onCancel={(event) => { event.preventDefault(); settle(false); }}
      // 창 바깥(어두운 배경)을 누르면 취소. 창 안쪽은 .panel이 덮고 있어 dialog 자신이 눌린 경우만 바깥이다.
      onMouseDown={(event) => { if (event.target === event.currentTarget) settle(false); }}
      onKeyDown={(event) => {
        // Tab이 브라우저 주소창으로 빠지지 않고 두 버튼 사이를 돈다.
        if (event.key !== "Tab") return;
        const first = cancelRef.current;
        const last = confirmRef.current;
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }}
    >
      <div className={styles.panel}>
        <h2 id={titleId}>{request.title}</h2>
        {request.description && <p id={descriptionId}>{request.description}</p>}
        <div className={styles.actions}>
          <button ref={cancelRef} type="button" onClick={() => settle(false)}>{request.cancelLabel ?? "취소"}</button>
          <button ref={confirmRef} type="button" className={request.danger ? styles.danger : styles.primary} onClick={() => settle(true)}>
            {request.confirmLabel ?? "확인"}
          </button>
        </div>
      </div>
    </dialog>
  );
}
