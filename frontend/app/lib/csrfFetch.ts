import { API_ENDPOINTS } from "./api";

const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
// Spring Security가 CSRF 검사에서 거절하면 accessDeniedHandler가 이 코드로 답한다.
const ACCESS_DENIED_CODE = "AUTH_002";

let cachedCsrfToken: string | null = null;

function isUnsafeMethod(method: string) {
  return !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method.toUpperCase());
}

async function getCsrfToken() {
  if (cachedCsrfToken) {
    return cachedCsrfToken;
  }

  const response = await fetch(API_ENDPOINTS.csrf, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("CSRF 토큰을 발급받지 못했습니다.");
  }

  const data: { token: string } = await response.json();
  cachedCsrfToken = data.token;
  return cachedCsrfToken;
}

async function isCsrfRejection(response: Response) {
  if (response.status !== 403) return false;
  const body = await response.clone().json().catch(() => null);
  return body?.code === ACCESS_DENIED_CODE;
}

export async function csrfFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  const method = (init.method ?? "GET").toUpperCase();
  const send = async () => {
    const headers = new Headers(init.headers);
    if (isUnsafeMethod(method)) {
      headers.set(CSRF_HEADER_NAME, await getCsrfToken());
    }
    return fetch(input, {
      ...init,
      method,
      headers,
      credentials: init.credentials ?? "include",
    });
  };

  const response = await send();
  // 다른 탭의 로그아웃이나 로그인으로 토큰이 바뀌면 캐시한 토큰이 거절된다. 한 번만 새 토큰으로 다시 보낸다.
  // CSRF 거절은 컨트롤러에 닿기 전에 일어나므로 다시 보내도 같은 요청이 두 번 처리되지 않는다.
  if (isUnsafeMethod(method) && cachedCsrfToken && (await isCsrfRejection(response))) {
    cachedCsrfToken = null;
    return send();
  }
  return response;
}
