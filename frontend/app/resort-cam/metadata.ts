import { notFound } from "next/navigation";
import { publicPageMetadata } from "../lib/siteMetadata";
import { getResortCameras } from "./data";
import { cameraSlug, resortSlug } from "./types";

export async function webcamMetadata(resortPath: string, cameraPath?: string) {
  const resorts = await getResortCameras();
  const resort = resorts.find(item => resortSlug(item.code) === resortPath);
  if (!resort) notFound();
  const camera = cameraPath ? resort.cameras.find(item => cameraSlug(item.code) === cameraPath) : undefined;
  if (cameraPath && !camera) notFound();
  const path = `/resort-cam/${resortSlug(resort.code)}${camera ? `/${cameraSlug(camera.code)}` : ""}`;
  return publicPageMetadata(
    camera ? `${resort.name} ${camera.name} 웹캠` : `${resort.name} 스키장 웹캠·슬로프캠`,
    camera
      ? `${resort.name} ${camera.name} 웹캠을 확인하세요. 리조트에서 제공하는 카메라 영상과 원본 송출 상태를 안내합니다.`
      : `${resort.regionName} ${resort.name}의 스키장 웹캠과 슬로프캠을 확인하세요. 카메라 ${resort.cameras.length}개와 원본 송출 상태를 안내합니다.`,
    path,
  );
}
