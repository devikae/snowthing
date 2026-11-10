import { ResortCamRoute } from "../../ResortCamRoute";
import { findWebcamOrNotFound, webcamMetadata } from "../../metadata";

export async function generateMetadata({ params }: { params: Promise<{ resortCode: string; cameraCode: string }> }) {
  const { resortCode, cameraCode } = await params;
  return webcamMetadata(resortCode, cameraCode);
}

export default async function ResortCamDetailPage({
  params,
}: {
  params: Promise<{ resortCode: string; cameraCode: string }>;
}) {
  const { resortCode, cameraCode } = await params;
  await findWebcamOrNotFound(resortCode, cameraCode);
  return <ResortCamRoute resortPath={resortCode} cameraPath={cameraCode} />;
}
