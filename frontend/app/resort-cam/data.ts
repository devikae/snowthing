import "server-only";
import { cache } from "react";

import { API_ENDPOINTS } from "../lib/api";
import type { ResortCameraGroup } from "./types";
import { resolveCameraSourceUrl, sortResortCameras } from "./types";
import { updateOfficialCameraSource } from "./sourceUpdates";
import { FALLBACK_RESORTS } from "./fallback-data";

async function loadResortCameras(): Promise<ResortCameraGroup[]> {
  let resorts = FALLBACK_RESORTS;
  try {
    const response = await fetch(API_ENDPOINTS.resortCams.list, {
      cache: "no-store",
      signal: AbortSignal.timeout(1500),
    });
    if (response.ok) {
      const body = (await response.json()) as { resorts: ResortCameraGroup[] };
      if (body.resorts?.length) resorts = body.resorts;
    }
  } catch {
    // Keep the local fallback when the metadata API is unavailable.
  }
  return sortResortCameras(resorts.map(resort => ({
      ...resort,
      cameras: resort.cameras.map(camera => updateOfficialCameraSource(resort.code, {
        ...camera,
        sourceUrl: resolveCameraSourceUrl(camera.sourceUrl),
      })),
    })));
}

export const getResortCameras = cache(loadResortCameras);
