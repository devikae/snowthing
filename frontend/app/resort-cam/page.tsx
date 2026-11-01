import { getResortCameras } from "./data";
import { ResortCamView } from "./ResortCamView";
import { publicPageMetadata } from "../lib/siteMetadata";

export const metadata = publicPageMetadata(
  "스키장 웹캠·슬로프캠 모아보기",
  "곤지암·웰리힐리·모나용평·하이원 등 전국 스키장 웹캠과 슬로프캠을 한곳에서 확인하세요. 리조트별 카메라와 원본 송출 상태를 안내합니다.",
  "/resort-cam",
);

export default async function ResortCamIndexPage() {
  const resorts = await getResortCameras();
  return <ResortCamView resorts={resorts} selectedResortCode={resorts[0]?.code ?? ""} />;
}
