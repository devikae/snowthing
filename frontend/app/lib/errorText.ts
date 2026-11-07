/** 화면에 보여 줄 오류 문구. 연결 실패의 브라우저 문구(Failed to fetch)는 영어라 대체 문구를 쓴다. */
export function errorText(error: unknown, fallback: string) {
  if (error instanceof TypeError || !(error instanceof Error)) return fallback;
  return error.message;
}
