import type { Metadata } from "next";
import { detailMetadata, fetchPreview, plainTextSummary } from "../../lib/sharePreview";
import CarpoolDetailClient from "./CarpoolDetailClient";

interface CarpoolPreview {
  title: string;
  content: string;
  departureRegion: string;
  destinationResortName: string;
  departureAt: string;
}

type Props = { params: Promise<{ publicId: string }> };

const KST_DATE = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric", weekday: "short" });

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { publicId } = await params;
  const carpool = await fetchPreview<CarpoolPreview>(`/carpools/${encodeURIComponent(publicId)}`);
  if (!carpool) return {};
  const route = `${carpool.departureRegion} 출발, ${carpool.destinationResortName}, ${KST_DATE.format(new Date(carpool.departureAt))}`;
  const body = plainTextSummary(carpool.content, 80);
  return detailMetadata({
    title: carpool.title,
    description: body ? `${route}. ${body}` : route,
    path: `/carpool/${encodeURIComponent(publicId)}`,
  });
}

export default function CarpoolDetailPage() {
  return <CarpoolDetailClient />;
}
