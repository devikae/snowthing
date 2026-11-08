import type { Metadata } from "next";
import { detailMetadata, fetchPreview, firstImageUrl, plainTextSummary } from "../../lib/sharePreview";
import MarketDetailClient from "./MarketDetailClient";

interface MarketPreview {
  title: string;
  content: string;
  price: number;
  negotiable: boolean;
  free: boolean;
  images: string[] | null;
  coverImageUrl: string | null;
}

type Props = { params: Promise<{ publicId: string }> };

function priceText({ price, negotiable, free }: MarketPreview) {
  if (free) return "무료 나눔";
  return `${price.toLocaleString("ko-KR")}원${negotiable ? " (가격 협의 가능)" : ""}`;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { publicId } = await params;
  // 상세 API는 viewed_posts 쿠키에 판매글 ID가 없으면 조회수를 올린다. 미리보기 조회는 조회수에 넣지 않는다.
  const preview = await fetchPreview(
    `/market-listings/${encodeURIComponent(publicId)}`,
    (listing: MarketPreview) => {
      const price = priceText(listing);
      const body = plainTextSummary(listing.content, 80);
      const images = listing.coverImageUrl ? [listing.coverImageUrl, ...(listing.images ?? [])] : listing.images;
      return { title: listing.title, description: body ? `${price}. ${body}` : price, image: firstImageUrl(images, listing.content) };
    },
    { Cookie: `viewed_posts=[${publicId}]` },
  );
  if (!preview) return {};
  return detailMetadata({ ...preview, path: `/market/${encodeURIComponent(publicId)}` });
}

export default function MarketDetailPage({ params }: Props) {
  return <MarketDetailClient params={params} />;
}
