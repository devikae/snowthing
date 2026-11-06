import type { Metadata } from "next";
import { API_BASE_URL } from "../../../lib/api";
import { GroupApp } from "./GroupApp";

const PREVIEW_TIMEOUT_MS = 2000;
const DESCRIPTION = "테스트하고 들어오면 이 모임 사람들과 나는 어떤 사이인지 지도로 볼 수 있어요. 재미로 보는 궁합이에요.";

async function fetchGroupName(publicId: string) {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/character-groups/${encodeURIComponent(publicId)}`, { cache: "no-store", signal: AbortSignal.timeout(PREVIEW_TIMEOUT_MS) });
    if (!response.ok) return null;
    const data: { name: string } = await response.json();
    return data.name;
  } catch {
    return null;
  }
}

// 모임 페이지는 검색에 나오지 않게 한다. 링크 미리보기 문구만 모임 이름으로 만든다.
export async function generateMetadata({ params }: { params: Promise<{ publicId: string }> }): Promise<Metadata> {
  const { publicId } = await params;
  const name = await fetchGroupName(publicId);
  const title = `${name ?? "우리 모임"} 관계 지도 | 눈팅 SnowThing`;
  return {
    title,
    description: DESCRIPTION,
    robots: { index: false, follow: false },
    openGraph: { type: "website", title, description: DESCRIPTION, siteName: "눈팅", locale: "ko_KR", images: [{ url: "/character-test/og-group.png", width: 1200, height: 630, alt: "나랑 제일 잘 맞는 사람은 누구" }] },
    twitter: { card: "summary_large_image", title, description: DESCRIPTION },
  };
}

export default async function CharacterGroupPage({ params }: { params: Promise<{ publicId: string }> }) {
  const { publicId } = await params;
  return <><header className="ct-heading"><h1>스키장 관계 지도</h1></header><GroupApp publicId={publicId} /></>;
}
