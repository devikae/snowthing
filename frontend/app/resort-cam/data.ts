import "server-only";
import { cache } from "react";

import { API_ENDPOINTS } from "../lib/api";
import type { ResortCameraGroup } from "./types";
import { resolveCameraSourceUrl, sortResortCameras } from "./types";
import { updateOfficialCameraSource } from "./sourceUpdates";
import { FALLBACK_RESORTS } from "./fallback-data";

// 카메라 목록은 자주 바뀌지 않는다. 카메라를 누를 때마다 백엔드 왕복을 기다리지 않게 서버 프로세스 안에 5분 보관한다.
// 대체 데이터(fallback)는 보관하지 않아 백엔드가 살아나면 다음 요청에서 바로 실제 목록을 받는다.
// 페이지는 force-dynamic 그대로라 빌드 때 받은 값이 굳지 않는다.
const CAMERA_LIST_TTL_MS = 5 * 60 * 1000;
let cachedResorts: { at: number; resorts: ResortCameraGroup[] } | null = null;

async function fetchResortList(): Promise<ResortCameraGroup[] | null> {
  if (cachedResorts && Date.now() - cachedResorts.at < CAMERA_LIST_TTL_MS) return cachedResorts.resorts;
  try {
    const response = await fetch(API_ENDPOINTS.resortCams.list, {
      cache: "no-store",
      signal: AbortSignal.timeout(1500),
    });
    if (response.ok) {
      const body = (await response.json()) as { resorts: ResortCameraGroup[] };
      if (body.resorts?.length) {
        cachedResorts = { at: Date.now(), resorts: body.resorts };
        return body.resorts;
      }
    }
  } catch {
    // Keep the local fallback when the metadata API is unavailable.
  }
  // 백엔드가 잠깐 안 될 때는 대체 데이터보다 마지막으로 받은 실제 목록이 낫다.
  return cachedResorts?.resorts ?? null;
}

async function loadResortCameras(): Promise<ResortCameraGroup[]> {
  const resorts = (await fetchResortList()) ?? FALLBACK_RESORTS;
  return sortResortCameras(resorts.map(resort => ({
      ...resort,
      cameras: resort.cameras.map(camera => updateOfficialCameraSource(resort.code, {
        ...camera,
        sourceUrl: resolveCameraSourceUrl(camera.sourceUrl),
      })),
    })));
}

export const getResortCameras = cache(loadResortCameras);
