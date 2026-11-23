/**
 * 화면에 보여 줄 오류 문구. 화면 코드가 직접 던진 Error의 문구만 그대로 쓴다.
 * 브라우저가 만든 오류는 영어 문구라 대체 문구를 쓴다.
 * - TypeError: 연결 실패(Failed to fetch)
 * - SyntaxError: 앞단이 HTML 오류 페이지로 답해 res.json()이 실패한 경우(Unexpected token '<' ...)
 * - DOMException: 시간 초과, 요청 취소(signal timed out, The operation was aborted)
 */
export function errorText(error: unknown, fallback: string) {
  if (!(error instanceof Error) || error instanceof TypeError || error instanceof SyntaxError) return fallback;
  if (typeof DOMException !== "undefined" && error instanceof DOMException) return fallback;
  return error.message || fallback;
}
