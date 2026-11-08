/**
 * Wowza는 시청자마다 접속 번호를 붙여 재생 목록과 조각 이름을 만든다(chunklist_w123.m3u8, media_w123_45.ts).
 * 그대로 두면 같은 캠을 봐도 조각 주소가 사람마다 달라 CDN 캐시가 다시 쓰이지 않는다.
 * 시청자에게는 접속 번호 없는 이름을 주고, 원본에는 고정 번호 하나로 요청한다.
 * Wowza는 처음 보는 번호도 받아 주고 조각 순번은 모든 접속에 공통이다.
 */
const SHARED_SESSION = "w1";

/** 원본 재생 목록의 접속 번호와 쿼리(토큰)를 지운다. */
export function toPublicWowzaManifest(manifest: string): string {
  return manifest
    .replace(/chunklist_w\d+\.m3u8(\?[^\s]*)?/g, "chunklist.m3u8")
    .replace(/media_w\d+_(\d+)\.(ts|aac|m4s|mp4)(\?[^\s]*)?/g, "media_$1.$2");
}

/** 시청자가 요청한 이름을 원본 이름으로 바꾼다. 접속 번호가 붙은 예전 이름도 받는다. */
export function toUpstreamWowzaFile(file: string): string {
  if (/^chunklist(_w\d+)?\.m3u8$/.test(file)) {
    return `chunklist_${SHARED_SESSION}.m3u8`;
  }
  const media = file.match(/^media(?:_w\d+)?_(\d+)\.(ts|aac|m4s|mp4)$/);
  if (media) {
    return `media_${SHARED_SESSION}_${media[1]}.${media[2]}`;
  }
  return file;
}
