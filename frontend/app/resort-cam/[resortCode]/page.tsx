import { ResortCamRoute } from "../ResortCamRoute";

export default async function ResortCamResortPage({ params }: { params: Promise<{ resortCode: string }> }) {
  const { resortCode } = await params;
  return <ResortCamRoute resortPath={resortCode} />;
}
