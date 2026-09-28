export type CameraSourceType = "HLS" | "YOUTUBE" | "IFRAME" | "EXTERNAL_LINK";

export interface ResortCameraItem {
  code: string;
  name: string;
  sourceType: CameraSourceType;
  sourceUrl: string;
  externalPageUrl: string | null;
  displayOrder: number;
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
