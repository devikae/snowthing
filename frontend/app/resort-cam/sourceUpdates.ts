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
  return updateOffAirCamera(resortCode, updateOfficialPage(resortCode, camera));
}

// 공식 웹캠 페이지 주소가 바뀐 리조트. 확인한 옛 주소만 바꾼다. (2026-11-20 확인)
const officialPageMoves: Record<string, { from: string; to: string }> = {
  OAK_VALLEY: { from: "https://app.oakvalley.co.kr/mobileapp/reserve/ski/webcam.do", to: "https://oakvalley.co.kr/ski/introduction/realtime" },
  WELLI_HILLI: { from: "https://m.wellihillipark.com/customer/webcam", to: "https://www.wellihillipark.com/home/customer/webcam" },
  VIVALDI: { from: "https://mice.sonohotelsresorts.com/daemyung.vp.utill.09_02_02_01.ds/dmparse.dm?areaType=S", to: "https://www.sonohotelsresorts.com/skiboard" },
};

function updateOfficialPage(resortCode: string, camera: ResortCameraItem): ResortCameraItem {
  const move = Object.hasOwn(officialPageMoves, resortCode) ? officialPageMoves[resortCode] : undefined;
  return move && camera.externalPageUrl === move.from ? { ...camera, externalPageUrl: move.to } : camera;
}

// 리조트 공식 페이지가 쓰는 원본에서도 영상이 나오지 않는 카메라. (2026-11-20 확인)
// 재생기를 띄워도 영상이 오지 않으므로 공식 페이지에서 보기로 돌린다. 원본이 다시 나오면 여기서 빼면 된다.
const offAirSources: Record<string, RegExp> = {
  // 공식 사이트 스크립트와 같은 주소. HTTP와 HTTPS 모두 502.
  OAK_VALLEY: /^https:\/\/cctv-oak9\.ktcdn\.co\.kr\/cctv\/ch(2|6|7|9)\.stream\/(chunklist|playlist)\.m3u8$/,
  // 공식 플레이어(ant.livecity)가 쓰는 주소. 404.
  JISAN: /^https:\/\/ant\.livecity\.co\.kr:5443\/jisancam\/streams\/jisan(1|4|5)\.m3u8$/,
  // 공식 웹캠 목록 API에는 있지만 원본 404.
  WELLI_HILLI: /^https:\/\/live\.wellihillipark\.com\/wellihillipark\/_definst_\/cam05\.stream\/playlist\.m3u8$/,
  // 영상 서버가 이 채널은 어떤 형식으로 요청해도 영상을 보내지 않는다.
  VIVALDI: /^\/vivaldi\.html\?channel=(8|10|5|9)&serial=TW0014A15451(&|$)/,
};

function updateOffAirCamera(resortCode: string, camera: ResortCameraItem): ResortCameraItem {
  const pattern = Object.hasOwn(offAirSources, resortCode) ? offAirSources[resortCode] : undefined;
  if (!pattern || camera.sourceType === "EXTERNAL_LINK" || !pattern.test(camera.sourceUrl)) return camera;
  return {
    ...camera,
    sourceType: "EXTERNAL_LINK",
    sourceIssue: "upstream-unavailable",
    availabilityCheckedAt: "2026-11-20",
    availabilityNote: "리조트 원본 영상이 지금 송출되지 않습니다. 리조트 공식 페이지에서 확인해 주세요.",
  };
}
