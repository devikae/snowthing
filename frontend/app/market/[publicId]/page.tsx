import type { Metadata } from "next";
import { detailMetadata, fetchPreview } from "../../lib/sharePreview";
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

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { publicId } = await params;
  const preview = await fetchPreview(
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
  if (!preview) return {};
  return detailMetadata({ ...preview, path: `/market/${encodeURIComponent(publicId)}` });
}

export default function MarketDetailPage({ params }: Props) {
  return <MarketDetailClient params={params} />;
}
