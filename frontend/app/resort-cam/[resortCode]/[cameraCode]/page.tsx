import { ResortCamRoute } from "../../ResortCamRoute";

export default async function ResortCamDetailPage({
  params,
}: {
  params: Promise<{ resortCode: string; cameraCode: string }>;
}) {
  const { resortCode, cameraCode } = await params;
  return <ResortCamRoute resortPath={resortCode} cameraPath={cameraCode} />;
}
