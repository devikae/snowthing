import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { hasSessionCookie } from "../../lib/sessionCookie.server";
import { isPublicId } from "../../lib/publicApi";
import { detailMetadata, fetchPreviewResult } from "../../lib/sharePreview";
import MarketDetailClient from "./MarketDetailClient";

// 로그인 없이 공개되는 공유 미리보기 API. 본문, 연락처, 판매자는 내려오지 않는다.
interface MarketPreview {
  title: string;
  price: number;
  negotiable: boolean;
  free: boolean;
  tradeStatus: "ON_SALE" | "RESERVED" | "SOLD";
  imageUrl: string | null;
}

const tradeLabels: Record<MarketPreview["tradeStatus"], string> = { ON_SALE: "", RESERVED: "예약 중", SOLD: "판매 완료" };

type Props = { params: Promise<{ publicId: string }> };

function priceText({ price, negotiable, free }: MarketPreview) {
  if (free) return "무료 나눔";
  return `${price.toLocaleString("ko-KR")}원${negotiable ? " (가격 협의 가능)" : ""}`;
}

function loadPreview(publicId: string) {
  // 주소의 값은 형식이 맞을 때만 백엔드 주소에 넣는다. 아니면 없는 글로 본다.
  if (!isPublicId(publicId)) return Promise.resolve({ value: null, missing: true });
  return fetchPreviewResult(
    `/market-listings/${encodeURIComponent(publicId)}/share-preview`,
    (listing: MarketPreview) => {
      const status = tradeLabels[listing.tradeStatus];
      const price = priceText(listing);
      return {
        title: listing.title,
        description: `${status ? `${status}. ` : ""}${price}. 눈팅 중고장터`,
        image: listing.imageUrl,
      };
    },
  );
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { publicId } = await params;
  const { value: preview } = await loadPreview(publicId);
  if (!preview) return {};
  return detailMetadata({ ...preview, path: `/market/${encodeURIComponent(publicId)}` });
}

// 공유 미리보기 API는 로그인과 상관없이 공개 판매글만 보여 준다. 404면 없는 글이거나 관리자가 숨긴 글이다.
// 숨긴 글은 관리자에게만 보이므로 로그인 쿠키가 있으면 404 페이지로 끊지 않고 브라우저가 다시 부르게 둔다.
// 미리보기 값은 60초 보관하므로 같은 글을 매번 다시 조회하지는 않는다.
export default async function MarketDetailPage({ params }: Props) {
  const { publicId } = await params;
  if ((await loadPreview(publicId)).missing && !(await hasSessionCookie())) notFound();
  return <MarketDetailClient params={params} />;
}
