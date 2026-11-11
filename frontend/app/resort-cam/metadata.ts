import { notFound } from "next/navigation";
import { publicPageMetadata } from "../lib/siteMetadata";
import { getResortCameras } from "./data";
import { cameraSlug, resortSlug } from "./types";

// 없는 리조트나 카메라 주소면 404로 답한다. 페이지에서도 불러야 한다.
// generateMetadata는 브라우저 요청에서 본문과 따로 스트리밍될 수 있어, 거기서만 부르면 상태 코드가 200으로 나간다.
export async function findWebcamOrNotFound(resortPath: string, cameraPath?: string) {
  const resorts = await getResortCameras();
  const resort = resorts.find(item => resortSlug(item.code) === resortPath);
  if (!resort) notFound();
  const camera = cameraPath ? resort.cameras.find(item => cameraSlug(item.code) === cameraPath) : undefined;
  if (cameraPath && !camera) notFound();
  return { resort, camera };
}

export async function webcamMetadata(resortPath: string, cameraPath?: string) {
  const { resort, camera } = await findWebcamOrNotFound(resortPath, cameraPath);
  const path = `/resort-cam/${resortSlug(resort.code)}${camera ? `/${cameraSlug(camera.code)}` : ""}`;
  return publicPageMetadata(
    camera ? `${resort.name} ${camera.name} 웹캠` : `${resort.name} 스키장 웹캠, 슬로프캠`,
    camera
      ? `${resort.name} ${camera.name} 웹캠을 확인하세요. 리조트에서 제공하는 카메라 영상과 원본 송출 상태를 안내합니다.`
      : `${resort.regionName} ${resort.name}의 스키장 웹캠과 슬로프캠을 확인하세요. 카메라 ${resort.cameras.length}개와 원본 송출 상태를 안내합니다.`,
    path,
  );
}
