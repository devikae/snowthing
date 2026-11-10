import { API_V1_URL } from "./api";

// 서버 컴포넌트가 첫 화면 데이터를 받을 때 쓴다. 비로그인 기준 공개 데이터만 받는다.
// 방문자 쿠키는 넘기지 않는다. 넘기면 로그인 사용자 전용 값(작성자 여부, 좋아요 여부)이 HTML에 섞이고,
// 나중에 캐시를 붙였을 때 다른 사람에게 그대로 나갈 수 있다. 로그인 상태는 브라우저가 이어 받는다.
// 백엔드가 느리면 첫 HTML 전체가 늦어지므로 짧게 끊고, 실패하면 null을 돌려 브라우저가 기존처럼 다시 부르게 한다.
// 글을 쓰고 돌아왔을 때 예전 목록이 보이지 않게 Next 데이터 캐시(revalidate)는 쓰지 않는다.
const PUBLIC_FETCH_TIMEOUT_MS = 2000;

const PUBLIC_ID_PATTERN = /^[0-9a-fA-F-]{36}$/;

export function isPublicId(value: string) {
  return PUBLIC_ID_PATTERN.test(value);
}

export async function fetchPublicJson<T>(path: string, headers?: Record<string, string>): Promise<T | null> {
  return (await fetchPublicResult<T>(path, headers)).data;
}

// missing은 백엔드가 404로 답한 경우다. 시간 초과나 다른 실패는 missing이 아니다.
export async function fetchPublicResult<T>(path: string, headers?: Record<string, string>): Promise<{ data: T | null; missing: boolean }> {
  try {
    const response = await fetch(`${API_V1_URL}${path}`, {
      cache: "no-store",
      headers,
      signal: AbortSignal.timeout(PUBLIC_FETCH_TIMEOUT_MS),
    });
    if (!response.ok) return { data: null, missing: response.status === 404 };
    return { data: (await response.json()) as T, missing: false };
  } catch {
    return { data: null, missing: false };
  }
}
