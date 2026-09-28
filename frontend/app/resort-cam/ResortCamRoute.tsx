"use client";

import { ResortCamView } from "./ResortCamView";
import { useResortCameras } from "./ResortCamProvider";
import { cameraSlug, resortSlug } from "./types";

export function ResortCamRoute({ resortPath, cameraPath }: { resortPath: string; cameraPath?: string }) {
  const resorts = useResortCameras();
  const resort = resorts.find((item) => resortSlug(item.code) === resortPath);
  const camera = cameraPath == null
    ? undefined
    : resort?.cameras.find((item) => cameraSlug(item.code) === cameraPath);
  const invalidMessage = resorts.length > 0 && !resort
    ? "존재하지 않는 리조트입니다."
    : cameraPath != null && resort != null && !camera
      ? "존재하지 않는 카메라입니다."
      : undefined;

  return (
    <ResortCamView
      resorts={resorts}
      selectedResortCode={resort?.code ?? ""}
      selectedCameraCode={camera?.code}
      invalidMessage={invalidMessage}
    />
  );
}
