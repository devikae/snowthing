// 브라우저에서 백엔드 API를 부를 때 반복되는 응답 처리 모음. 쓰기 요청(CSRF)은 csrfFetch.ts가 맡는다.

type ApiErrorBody = { code?: string; message?: string };

async function readJsonOrNull(response: Response): Promise<ApiErrorBody | null> {
  return response.json().catch(() => null);
}

/** 실패 응답의 서버 문구. JSON이 아니거나 문구가 비어 있으면 fallback을 쓴다. */
export async function readErrorMessage(response: Response, fallback: string) {
  const message = (await readJsonOrNull(response))?.message;
  return typeof message === "string" && message ? message : fallback;
}

// 프록시나 서버 앞단이 HTML로 답하면 본문 문구가 없다. 그때는 상태 코드로 원인을 짐작해 안내한다.
const STATUS_MESSAGES: Record<number, string> = {
  401: "로그인이 풀렸습니다. 다시 로그인해 주세요.",
  403: "이 작업을 할 권한이 없습니다.",
  413: "보내는 내용이 너무 큽니다. 본문 길이를 줄이거나 사진 수를 줄여 다시 시도해 주세요.",
  429: "요청이 많습니다. 잠시 후 다시 시도해 주세요.",
};

/** 실패 응답에서 서버 코드와 문구를 꺼낸다. JSON이 아니거나 문구가 없으면 상태 코드 안내, 그것도 없으면 fallback을 쓴다. */
export async function readApiError(response: Response, fallback: string) {
  const statusMessage = STATUS_MESSAGES[response.status] ?? fallback;
  const body = await readJsonOrNull(response);
  if (!body) return { code: undefined, message: statusMessage };
  return { code: body.code, message: body.message?.trim() || statusMessage };
}

/** 로그인 쿠키를 실어 GET으로 JSON을 받는다. 실패하면 failMessage로 Error를 던진다. */
export async function getJson<T>(url: string, failMessage: string): Promise<T> {
  const response = await fetch(url, { credentials: "include" });
  if (!response.ok) throw new Error(failMessage);
  return response.json() as Promise<T>;
}
