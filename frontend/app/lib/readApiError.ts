type ApiErrorBody = { code?: string; message?: string };

// 프록시나 서버 앞단이 HTML로 답하면 본문 문구가 없다. 그때는 상태 코드로 원인을 짐작해 안내한다.
const STATUS_MESSAGES: Record<number, string> = {
  401: "로그인이 풀렸습니다. 다시 로그인해 주세요.",
  403: "이 작업을 할 권한이 없습니다.",
  413: "보내는 내용이 너무 큽니다. 본문 길이를 줄이거나 사진 수를 줄여 다시 시도해 주세요.",
  429: "요청이 많습니다. 잠시 후 다시 시도해 주세요.",
};

/** 실패 응답에서 서버 문구를 꺼낸다. JSON이 아니거나 문구가 없으면 상태 코드 안내, 그것도 없으면 fallback을 쓴다. */
export async function readApiError(response: Response, fallback: string) {
  const statusMessage = STATUS_MESSAGES[response.status] ?? fallback;
  try {
    const body = (await response.json()) as ApiErrorBody;
    return { code: body.code, message: body.message?.trim() || statusMessage };
  } catch {
    return { code: undefined, message: statusMessage };
  }
}
