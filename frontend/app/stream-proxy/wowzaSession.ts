/**
 * Wowza는 시청자마다 접속 번호를 붙여 재생 목록과 조각 이름을 만든다(chunklist_w123.m3u8, media_w123_45.ts).
 * 그대로 두면 같은 캠을 봐도 조각 주소가 사람마다 달라 CDN 캐시가 다시 쓰이지 않는다.
 * 시청자에게는 접속 번호 없는 이름을 주고, 원본에는 서버가 받아 둔 번호 하나로 요청한다.
 *
 * 접속 번호는 원본 재생 목록(playlist.m3u8)이 스트림마다 따로 발급하고, 발급하지 않은 번호는 403으로 거절한다.
 * 그래서 스트림별로 발급받은 번호를 기억해 모든 시청자가 함께 쓰고, 403이 오면 번호를 새로 받아 한 번만 다시 요청한다.
 */

/** 원본 스트림 하나를 프록시가 요청하는 방법. */
export interface WowzaSource {
  /** 접속 번호를 함께 쓰는 단위. 스트림마다 달라야 한다. */
  key: string;
  /** 접속 번호를 발급받을 원본 재생 목록 주소. */
  playlistUrl: () => Promise<string>;
  /** 원본 파일 이름(접속 번호 포함)을 실제 요청 주소로 만든다. */
  fileUrl: (upstreamFile: string) => Promise<string>;
  init?: RequestInit;
  /** 403을 받았을 때 함께 버릴 상태(토큰 등). */
  onForbidden?: () => void;
}

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

const sessions = new Map<string, Promise<string>>();

/** 원본 재생 목록의 접속 번호와 쿼리(토큰)를 지운다. */
export function toPublicWowzaManifest(manifest: string): string {
  return manifest
    .replace(/chunklist_w\d+\.m3u8(\?[^\s]*)?/g, "chunklist.m3u8")
    .replace(/media_w\d+_(\d+)\.(ts|aac|m4s|mp4)(\?[^\s]*)?/g, "media_$1.$2");
}

/** 원본 재생 목록이 발급한 접속 번호(w123). 없으면 null. */
export function issuedWowzaSession(manifest: string): string | null {
  return manifest.match(/chunklist_(w\d+)\.m3u8/)?.[1] ?? null;
}

/** 시청자가 요청한 이름을 원본 이름으로 바꾼다. 접속 번호가 붙은 예전 이름도 받는다. */
export function toUpstreamWowzaFile(file: string, session: string): string {
  if (/^chunklist(_w\d+)?\.m3u8$/.test(file)) {
    return `chunklist_${session}.m3u8`;
  }
  const media = file.match(/^media(?:_w\d+)?_(\d+)\.(ts|aac|m4s|mp4)$/);
  if (media) {
    return `media_${session}_${media[1]}.${media[2]}`;
  }
  return file;
}

/** 시청자가 받은 원본 재생 목록의 번호를 기억해 둔다. 이미 쓰는 번호가 있으면 그대로 둔다. */
export function rememberWowzaSession(key: string, manifest: string): void {
  const session = issuedWowzaSession(manifest);
  if (session && !sessions.has(key)) sessions.set(key, Promise.resolve(session));
}

function sessionFor(source: WowzaSource, fetcher: Fetcher): Promise<string> {
  const cached = sessions.get(source.key);
  if (cached) return cached;
  // 동시에 들어온 요청이 번호를 따로 받지 않도록 발급 중인 Promise를 함께 쓴다.
  const pending = (async () => {
    const response = await fetcher(await source.playlistUrl(), { ...source.init, cache: "no-store" });
    if (!response.ok) {
      await response.body?.cancel();
      throw new Error(`Wowza playlist ${response.status}`);
    }
    const session = issuedWowzaSession(await response.text());
    if (!session) throw new Error("Wowza playlist has no session");
    return session;
  })();
  sessions.set(source.key, pending);
  pending.catch(() => forget(source.key, pending));
  return pending;
}

function forget(key: string, session: Promise<string>) {
  // 그사이 다른 요청이 새 번호를 받았다면 지우지 않는다.
  if (sessions.get(key) === session) sessions.delete(key);
}

/** 접속 번호를 붙여 조각 목록이나 영상 조각을 받는다. 403이면 번호를 새로 받아 한 번만 다시 요청한다. */
export async function fetchWowzaFile(source: WowzaSource, file: string, fetcher: Fetcher = fetch): Promise<Response> {
  for (let attempt = 0; ; attempt++) {
    const pending = sessionFor(source, fetcher);
    const session = await pending;
    const response = await fetcher(await source.fileUrl(toUpstreamWowzaFile(file, session)), { ...source.init, cache: "no-store" });
    if (response.status !== 403 || attempt > 0) return response;
    await response.body?.cancel();
    forget(source.key, pending);
    source.onForbidden?.();
  }
}
