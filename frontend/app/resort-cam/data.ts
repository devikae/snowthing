import "server-only";

import { API_ENDPOINTS } from "../lib/api";
import type { ResortCameraGroup } from "./types";
import { FALLBACK_RESORTS } from "./fallback-data";

export async function getResortCameras(): Promise<ResortCameraGroup[]> {
  try {
    const response = await fetch(API_ENDPOINTS.resortCams.list, {
      cache: "no-store",
      signal: AbortSignal.timeout(1500),
    });
    if (!response.ok) return FALLBACK_RESORTS;
    const body = (await response.json()) as { resorts: ResortCameraGroup[] };
    if (!body.resorts || body.resorts.length === 0) return FALLBACK_RESORTS;
    return body.resorts;
  } catch {
    return FALLBACK_RESORTS;
  }
}
