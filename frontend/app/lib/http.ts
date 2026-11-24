// 브라우저에서 백엔드 API를 부를 때 반복되는 응답 처리 모음. 쓰기 요청(CSRF)은 csrfFetch.ts가 맡는다.

type ApiErrorBody = { code?: string; message?: string };

async function readJsonOrNull(response: Response): Promise<ApiErrorBody | null> {
  return response.json().catch(() => null);
}

/** 실패 응답의 문구만 필요할 때. 규칙은 readApiError와 같다. */
export async function readErrorMessage(response: Response, fallback: string) {
  return (await readApiError(response, fallback)).message;
}

// 프록시나 서버 앞단이 HTML이나 빈 본문으로 답하면 서버 문구가 없다. 그때는 상태 코드로 원인을 짐작해 안내한다.
const STATUS_MESSAGES: Record<number, string> = {
  401: "로그인이 풀렸습니다. 다시 로그인해 주세요.",
  403: "이 작업을 할 권한이 없습니다.",
  413: "보내는 내용이 너무 큽니다. 본문 길이를 줄이거나 사진 수를 줄여 다시 시도해 주세요.",
  429: "요청이 많습니다. 잠시 후 다시 시도해 주세요.",
};
// 502, 503, 504처럼 서버가 답하지 못한 경우. 화면별 대체 문구("등록하지 못했습니다")보다 원인이 분명하다.
const SERVER_ERROR_MESSAGE = "서버가 잠시 응답하지 못하고 있습니다. 잠시 후 다시 시도해 주세요.";

/** 429 응답의 Retry-After(초)가 있으면 기다릴 시간을 알려 준다. 없거나 날짜 형식이면 null. */
function retryAfterMessage(response: Response) {
  const seconds = Number(response.headers.get("Retry-After"));
  if (response.status !== 429 || !Number.isFinite(seconds) || seconds <= 0) return null;
  const wait = seconds >= 60 ? `${Math.ceil(seconds / 60)}분` : `${Math.ceil(seconds)}초`;
  return `요청이 많습니다. ${wait} 뒤에 다시 시도해 주세요.`;
}

/**
 * 실패 응답에서 서버 코드와 문구를 꺼낸다. 화면은 응답 본문을 직접 읽지 말고 이 함수를 쓴다.
 * 1. 본문이 JSON이고 message가 있으면 서버 문구(429에 Retry-After가 있으면 기다릴 시간 안내).
 * 2. 본문이 HTML이거나 비어 있으면 상태 코드 안내(401, 403, 413, 429, 5xx).
 * 3. 그 밖에는 화면이 준 fallback.
 */
export async function readApiError(response: Response, fallback: string) {
  const status = response.status;
  const statusMessage = retryAfterMessage(response) ?? STATUS_MESSAGES[status] ?? (status >= 500 ? SERVER_ERROR_MESSAGE : fallback);
  const body = await readJsonOrNull(response);
  // JSON이 아니거나(null) 배열, 글자처럼 객체가 아닌 값이면 서버 문구가 없는 것으로 본다.
  if (!body || typeof body !== "object") return { status, code: undefined, message: statusMessage };
  const message = typeof body.message === "string" ? body.message.trim() : "";
  return { status, code: typeof body.code === "string" ? body.code : undefined, message: retryAfterMessage(response) ?? (message || statusMessage) };
}

/** 로그인 쿠키를 실어 GET으로 JSON을 받는다. 실패하면 failMessage로 Error를 던진다. */
export async function getJson<T>(url: string, failMessage: string): Promise<T> {
  const response = await fetch(url, { credentials: "include" });
  if (!response.ok) throw new Error(failMessage);
  return response.json() as Promise<T>;
}
