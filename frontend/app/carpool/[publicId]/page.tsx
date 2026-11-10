import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { detailMetadata, fetchPreviewResult, plainTextSummary } from "../../lib/sharePreview";
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

function loadPreview(publicId: string) {
  return fetchPreviewResult(`/carpools/${encodeURIComponent(publicId)}`, (carpool: CarpoolPreview) => {
    const route = `${carpool.departureRegion} 출발, ${carpool.destinationResortName}, ${KST_DATE.format(parseKst(carpool.departureAt))}`;
    const body = plainTextSummary(carpool.content, 80);
    return { title: carpool.title, description: body ? `${route}. ${body}` : route };
  });
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { publicId } = await params;
  const { value: preview } = await loadPreview(publicId);
  if (!preview) return {};
  return detailMetadata({ ...preview, path: `/carpool/${encodeURIComponent(publicId)}` });
}

// 카풀 상세 API는 로그인과 상관없이 삭제되거나 없는 글에만 404를 준다. 그때는 404 페이지로 답한다.
// 미리보기 값은 60초 보관하므로 같은 글을 매번 다시 조회하지는 않는다.
export default async function CarpoolDetailPage({ params }: Props) {
  const { publicId } = await params;
  if ((await loadPreview(publicId)).missing) notFound();
  return <CarpoolDetailClient />;
}
