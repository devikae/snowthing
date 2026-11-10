import { ResortCamRoute } from "../ResortCamRoute";
import { findWebcamOrNotFound, webcamMetadata } from "../metadata";

export async function generateMetadata({ params }: { params: Promise<{ resortCode: string }> }) {
  return webcamMetadata((await params).resortCode);
}

export default async function ResortCamResortPage({ params }: { params: Promise<{ resortCode: string }> }) {
  const { resortCode } = await params;
  await findWebcamOrNotFound(resortCode);
  return <ResortCamRoute resortPath={resortCode} />;
}
