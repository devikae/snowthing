import { API_ENDPOINTS } from "./api";

export interface Viewer {
  publicId: string;
  email: string;
  nickname: string;
  profileImageUrl: string | null;
  role?: string;
}

// 헤더, 페이지, 댓글, 작성자 메뉴가 각자 /members/me를 부르면 화면 하나에 같은 요청이 여러 번 나간다.
// 진행 중인 요청과 결과를 잠깐 공유한다. 로그인, 로그아웃, 프로필 변경 때는 clearViewerCache()로 비운다.
const VIEWER_CACHE_MS = 60_000;
let cache: { at: number; promise: Promise<Viewer | null>; value?: Viewer | null } | null = null;

export function fetchViewer(): Promise<Viewer | null> {
  if (cache && Date.now() - cache.at < VIEWER_CACHE_MS) return cache.promise;
  const entry: NonNullable<typeof cache> = { at: Date.now(), promise: Promise.resolve(null) };
  entry.promise = fetch(API_ENDPOINTS.members.me, { credentials: "include" })
    .then(async (response) => {
      if (!response.ok && response.status !== 401 && response.status !== 403) throw new Error(`members/me ${response.status}`);
      const value = response.ok ? ((await response.json()) as Viewer) : null;
      entry.value = value;
      return value;
    })
    .catch(() => {
      // 네트워크 오류와 서버 오류는 로그아웃 상태로 기억하지 않는다. 다음 화면에서 다시 묻는다.
      if (cache === entry) cache = null;
      return null;
    });
  cache = entry;
  return entry.promise;
}

// 이미 받은 값이 있으면 바로 돌려준다. 아직 모르면 undefined. 화면 이동 직후 헤더를 비우지 않고 그리는 데 쓴다.
export function peekViewer(): Viewer | null | undefined {
  if (!cache || Date.now() - cache.at >= VIEWER_CACHE_MS) return undefined;
  return cache.value;
}

export function clearViewerCache() {
  cache = null;
}

export function isAdminViewer(viewer: Viewer | null) {
  return viewer?.role === "ROLE_ADMIN";
}
