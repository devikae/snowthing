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

// 백엔드 departureAt은 시간대 없는 KST 시각(LocalDateTime)이다. 프론트 컨테이너는 UTC라 그대로 파싱하면 날짜가 밀린다.
function parseKst(value: string) {
  return new Date(/(Z|[+-]\d{2}:?\d{2})$/.test(value) ? value : `${value}+09:00`);
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { publicId } = await params;
  const preview = await fetchPreview(`/carpools/${encodeURIComponent(publicId)}`, (carpool: CarpoolPreview) => {
    const route = `${carpool.departureRegion} 출발, ${carpool.destinationResortName}, ${KST_DATE.format(parseKst(carpool.departureAt))}`;
    const body = plainTextSummary(carpool.content, 80);
    return { title: carpool.title, description: body ? `${route}. ${body}` : route };
  });
  if (!preview) return {};
  return detailMetadata({ ...preview, path: `/carpool/${encodeURIComponent(publicId)}` });
}

export default function CarpoolDetailPage() {
  return <CarpoolDetailClient />;
}
