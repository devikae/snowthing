import type { Metadata } from "next";
import Link from "next/link";
import { PageHeading } from "../components/PageHeading";
import { publicPageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata("라이더 도구", "시즌권 본전과 방문 목표를 계산하고 데크 길이를 확인하세요. 가입 없이 사용할 수 있습니다.", "/tools");

export default function ToolsPage() {
  return <><PageHeading title="라이더 도구" description="가입 없이 시즌 비용과 데크 길이를 계산해 보세요." />
    <section className="tools-directory"><Link href="/tools/season-pass"><span>시즌 준비</span><h2>시즌권 본전 계산기</h2><p>시즌권과 리프트권 비용을 비교해 본전까지 필요한 방문 횟수를 확인하세요.</p><strong>계산하기 →</strong></Link><Link href="/tools/season-pass-prices"><span>시즌 준비</span><h2>스키장 시즌권, 리프트권 가격 비교</h2><p>2026/27 시즌 스키장 13곳의 시즌권 값과 리프트권 값, 본전 횟수를 한 표로 볼 수 있어요.</p><strong>가격 보기 →</strong></Link><Link href="/tools/deck-size"><span>장비 선택</span><h2>데크 사이즈 계산기</h2><p>체중과 부츠 크기로 데크 길이의 참고 범위를 확인하세요.</p><strong>사이즈 확인하기 →</strong></Link><Link href="/season-log"><span>시즌 기록</span><h2>내 시즌 기록</h2><p>리조트를 다녀온 날을 남기면 시즌에 며칠 탔는지, 어디를 가장 많이 갔는지 정리해 드려요. 로그인이 필요해요.</p><strong>기록하기 →</strong></Link><Link href="/character-test"><span>재미로 보기</span><h2>스키장 캐릭터 테스트</h2><p>스키장 상황을 고르면 내 캐릭터 카드가 나오고, 같이 타는 사람들과의 관계 지도도 볼 수 있어요.</p><strong>테스트하기 →</strong></Link></section>
    <section className="tools-community"><h2>함께 타는 사람들의 이야기</h2><p>계산에 담기지 않는 현장 정보는 라이더에게 물어보세요.</p><div><Link href="/resort-reports">오늘의 설질</Link><Link href="/carpool">카풀과 동행</Link><Link href="/posts?category=QNA">장비 질문</Link></div></section>
  </>;
}
