export type CameraSourceType = "HLS" | "YOUTUBE" | "IFRAME" | "EXTERNAL_LINK";

export interface ResortCameraItem {
  code: string;
  name: string;
  sourceType: CameraSourceType;
  sourceUrl: string;
  externalPageUrl: string | null;
  displayOrder: number;
  availabilityNote?: string;
  sourceIssue?: "upstream-unavailable" | "not-published" | "provider-restricted";
  availabilityCheckedAt?: string;
}

export interface ResortCameraGroup {
  code: string;
  name: string;
  regionName: string;
  displayOrder: number;
  cameras: ResortCameraItem[];
}

export function resortSlug(code: string) {
  return code.toLowerCase().replaceAll("_", "-");
}

export function cameraSlug(code: string) {
  return code.toLowerCase().replaceAll("_", "-");
}

const RESORT_PRIORITY = ["KONJIAM", "WELLI_HILLI", "YONGPYONG", "HIGH1", "JISAN", "MUJU", "O2_RESORT"];

export function sortResortCameras(resorts: readonly ResortCameraGroup[]): ResortCameraGroup[] {
  const rank = (code: string) => {
    const index = RESORT_PRIORITY.indexOf(code);
    return index < 0 ? RESORT_PRIORITY.length : index;
  };
  return [...resorts].sort((left, right) => rank(left.code) - rank(right.code));
}

/** Preserve older stored URLs after the Next stream routes moved. */
export function resolveCameraSourceUrl(sourceUrl: string): string {
  return sourceUrl
    .replace(/^\/api\/high1-stream\//, "/stream-proxy/high1/")
    .replace(/^\/api\/muju-stream\//, "/stream-proxy/muju/")
    .replace(/^\/api\/o2-stream\//, "/stream-proxy/o2/");
}
