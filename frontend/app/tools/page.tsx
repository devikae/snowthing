import type { Metadata } from "next";
import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { publicPageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata("라이더 도구", "시즌권 본전과 방문 목표를 계산하고 데크 길이를 확인하세요. 가입 없이 사용할 수 있습니다.", "/tools");

export default function ToolsPage() {
  return <><PageHeading title="라이더 도구" description="가입 없이 시즌 비용과 데크 길이를 계산해 보세요." />
    <section className="tools-directory"><Link href="/tools/season-pass"><span>시즌 준비</span><h2>시즌권 본전 계산기</h2><p>시즌권과 리프트권 비용을 비교해 본전까지 필요한 방문 횟수를 확인하세요.</p><strong>계산하기 →</strong></Link><Link href="/tools/deck-size"><span>장비 선택</span><h2>데크 사이즈 계산기</h2><p>체중과 부츠 크기로 데크 길이의 참고 범위를 확인하세요.</p><strong>사이즈 확인하기 →</strong></Link><Link href="/character-test"><span>재미로 보기</span><h2>스키장 캐릭터 테스트</h2><p>스키장 상황을 고르면 내 캐릭터 카드가 나오고, 같이 타는 사람들과의 관계 지도도 볼 수 있어요.</p><strong>테스트하기 →</strong></Link></section>
    <section className="tools-community"><h2>함께 타는 사람들의 이야기</h2><p>계산에 담기지 않는 현장 정보는 라이더에게 물어보세요.</p><div><Link href="/resort-reports">오늘의 설질</Link><Link href="/carpool">카풀·동행</Link><Link href="/posts?category=QNA">장비 질문</Link></div></section>
  </>;
}
