// 화면 어디서나 부르는 공통 안내: 잠깐 떴다 사라지는 토스트와 확인 창.
// 브라우저 기본 alert, confirm 대신 쓴다. 실제 그리기는 루트 레이아웃의 FeedbackHost가 맡는다.
// 훅이 아니라 함수라서 이벤트 처리기 안에서 `if (!(await confirm({ ... }))) return;`처럼 바로 쓸 수 있다.

export type ToastKind = "success" | "error" | "info";
export type ToastItem = { id: number; kind: ToastKind; message: string };

export type ConfirmOptions = {
  /** 무엇을 할지 묻는 한 줄. */
  title: string;
  /** 결과나 되돌릴 수 있는지에 대한 설명. */
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** 삭제, 탈퇴, 차단처럼 되돌리기 어려운 동작. 확인 버튼을 강조색으로 그리고 처음 초점을 취소에 둔다. */
  danger?: boolean;
};
export type ConfirmRequest = ConfirmOptions & { resolve: (ok: boolean) => void };

export type FeedbackState = { toasts: ToastItem[]; confirm: ConfirmRequest | null };

/** 한 번에 보이는 토스트 수. 넘치면 오래된 것부터 지운다. */
const MAX_TOASTS = 3;

let state: FeedbackState = { toasts: [], confirm: null };
let nextId = 1;
const listeners = new Set<() => void>();

function setState(next: FeedbackState) {
  state = next;
  listeners.forEach((listener) => listener());
}

export function subscribeFeedback(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
export function getFeedbackState() { return state; }

function push(kind: ToastKind, message: string) {
  if (typeof window === "undefined" || !message) return;
  // 같은 문구가 이미 떠 있으면 새로 쌓지 않고 그 토스트를 새것으로 바꿔 시간만 다시 센다(연속 클릭).
  const rest = state.toasts.filter((item) => !(item.kind === kind && item.message === message));
  setState({ ...state, toasts: [...rest, { id: nextId++, kind, message }].slice(-MAX_TOASTS) });
}

export function dismissToast(id: number) {
  if (!state.toasts.some((item) => item.id === id)) return;
  setState({ ...state, toasts: state.toasts.filter((item) => item.id !== id) });
}

export const toast = {
  success: (message: string) => push("success", message),
  error: (message: string) => push("error", message),
  info: (message: string) => push("info", message),
};

/**
 * 확인 창을 띄우고 확인이면 true, 취소(취소 버튼, Esc, 바깥 클릭)면 false로 끝나는 Promise를 준다.
 * 이미 확인 창이 떠 있으면 새 창을 겹치지 않고 false를 준다(연속 클릭으로 같은 동작이 두 번 실행되지 않게).
 */
export function confirm(options: ConfirmOptions | string): Promise<boolean> {
  if (typeof window === "undefined" || state.confirm) return Promise.resolve(false);
  const normalized = typeof options === "string" ? { title: options } : options;
  return new Promise<boolean>((resolve) => {
    setState({ ...state, confirm: { ...normalized, resolve } });
  });
}

/** FeedbackHost가 확인 창을 닫을 때 부른다. */
export function settleConfirm(ok: boolean) {
  const request = state.confirm;
  if (!request) return;
  setState({ ...state, confirm: null });
  request.resolve(ok);
}
