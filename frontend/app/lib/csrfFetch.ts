import { API_ENDPOINTS } from "./api";

const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

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

export async function csrfFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  const method = (init.method ?? "GET").toUpperCase();
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
}
