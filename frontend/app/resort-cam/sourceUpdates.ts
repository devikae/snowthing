import type { ResortCameraItem } from "./types";

// Verified against each resort's own published camera list on 2026-11-01.
// Exact old URLs only: never overwrite a separately configured/custom source.
const phoenixUpdates: Record<string, { sourceUrl: string; name: string }> = {
  "yh_02.m3u8": { sourceUrl: "yh_02_02.m3u8", name: "포레스트 파크" },
  "ht_01.m3u8": { sourceUrl: "ht_01_01.m3u8", name: "잔디 광장" },
  "bc_02.m3u8": { sourceUrl: "bc_02_01.m3u8", name: "휘닉스 CC" },
  "bc_01.m3u8": { sourceUrl: "bc_01.m3u8", name: "루지랜드" },
  "yh_01.m3u8": { sourceUrl: "yh_01_01.m3u8", name: "태기산 나인 CC" },
  "mb_03.m3u8": { sourceUrl: "mb_03_02.m3u8", name: "몽블랑" },
};

export function updateOfficialCameraSource(resortCode: string, camera: ResortCameraItem): ResortCameraItem {
  const phoenixPrefix = "https://streaming.phoenixhnr.co.kr/hls/";
  if (resortCode === "PHOENIX" && camera.sourceUrl.startsWith(phoenixPrefix)) {
    const filename = camera.sourceUrl.slice(phoenixPrefix.length);
    const update = Object.hasOwn(phoenixUpdates, filename) ? phoenixUpdates[filename] : undefined;
    if (update) return { ...camera, name: update.name, sourceUrl: phoenixPrefix + update.sourceUrl };
    if (camera.sourceUrl === phoenixPrefix + "sp_01.m3u8") return {
      ...camera, sourceType: "EXTERNAL_LINK",
      sourceUrl: camera.externalPageUrl || "https://phoenixhnr.co.kr/page/pyeongchang/guide/operation/sketchMovie",
      availabilityNote: "현재 리조트 공식 공개 목록에 없는 카메라입니다.",
      sourceIssue: "not-published", availabilityCheckedAt: "2026-11-01",
    };
  }
  if (resortCode === "ALPENSIA" && camera.sourceUrl === "https://www.youtube.com/embed/I3azd8f2HhM") {
    return { ...camera, sourceUrl: "https://www.youtube.com/embed/vvRfdby4YxI" };
  }
  if (resortCode === "ELYSIAN" && camera.sourceUrl === "https://www.youtube.com/embed/O6_8VBu4FSA") {
    return { ...camera, sourceType: "EXTERNAL_LINK",
      externalPageUrl: "https://elysian.co.kr/about-gangchon/ski",
      availabilityNote: "현재 리조트 공식 웹캠에 재생 가능한 영상이 등록되지 않았습니다.",
      sourceIssue: "not-published", availabilityCheckedAt: "2026-11-01",
    };
  }
  if (resortCode === "EDEN_VALLEY") {
    const url = camera.sourceUrl.replace(/\/$/, "");
    if (url === "https://rtsp.me/embed/2kTsKt35") return {
      ...camera, sourceType: "EXTERNAL_LINK", sourceUrl: "https://rtsp.ru/embed/nKGZGiRD/",
      externalPageUrl: "https://www.edenvalley.co.kr/CS/cam_pop1.asp",
      availabilityNote: "영상 제공처에서 현재 재생을 제한하고 있습니다. 리조트 공식 페이지를 확인해 주세요.",
      sourceIssue: "provider-restricted", availabilityCheckedAt: "2026-11-01",
    };
    if (url === "https://rtsp.me/embed/ry9aTdQh") return {
      ...camera, sourceType: "EXTERNAL_LINK", sourceUrl: "https://rtsp.ru/embed/dh6R7d4k/",
      externalPageUrl: "https://www.edenvalley.co.kr/CS/cam_pop1.asp",
      availabilityNote: "영상 제공처에서 현재 재생을 제한하고 있습니다. 리조트 공식 페이지를 확인해 주세요.",
      sourceIssue: "provider-restricted", availabilityCheckedAt: "2026-11-01",
    };
  }
  const checkedUnavailable =
    (resortCode === "JISAN" && /^https:\/\/ant\.livecity\.co\.kr:5443\/jisancam\/streams\/jisan[1-5]\.m3u8$/.test(camera.sourceUrl)) ||
    (resortCode === "MUJU" && /^\/stream-proxy\/muju\/(02|03)\/playlist\.m3u8$/.test(camera.sourceUrl)) ||
    (resortCode === "OAK_VALLEY" && /^https:\/\/cctv-oak9\.ktcdn\.co\.kr\/cctv\/ch(2|6|7|9)\.stream\/(chunklist|playlist)\.m3u8$/.test(camera.sourceUrl));
  if (checkedUnavailable) return {
    ...camera,
    sourceIssue: "upstream-unavailable", availabilityCheckedAt: "2026-11-01",
    availabilityNote: "리조트 원본 영상에서도 연결 오류가 확인됐습니다. 잠시 후 다시 연결하거나 공식 페이지를 확인해 주세요.",
  };
  return camera;
}
