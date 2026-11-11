import type { Metadata } from "next";
import Link from "next/link";
import { PageHeading } from "../../components/PageHeading";
import { SEASON_OPENINGS } from "../../lib/seasonOpening";
import { RESORT_PASS_PRICES, SEASON_PASS_PRICE_CHECKED_AT } from "../../lib/seasonPassPrices";
import { publicPageMetadata } from "../../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata(
  "2026/27 스키장 시즌권 가격 비교",
  "26/27 시즌 하이원, 용평, 휘닉스, 비발디, 곤지암 등 13개 스키장의 시즌권 가격과 리프트권 가격, 할인가를 한 표로 비교하고 시즌권 본전 횟수를 확인하세요.",
  "/tools/season-pass-prices",
);

const won = (value: number) => `${value.toLocaleString("ko-KR")}원`;
// 시즌권 값을 할인 리프트권 1회 값으로 나눠 올림한 횟수. 교통비 같은 부대비용은 뺀다.
const breakEven = (pass: number, ticket: number) => Math.ceil(pass / ticket);

// 개장일 데이터는 리조트 코드 표기가 조금 다르다(WELLI_HILLI). 밑줄을 빼고 맞춘다.
const plainCode = (code: string) => code.replace(/_/g, "");
function openingText(code: string): string | null {
  const opening = SEASON_OPENINGS.find((item) => plainCode(item.resortCode) === plainCode(code));
  if (!opening) return null;
  const [, month, day] = opening.date.split("-").map(Number);
  return `${opening.confirmed ? "개장" : "개장 예상"} ${month}월 ${day}일`;
}

const FAQ = [
  {
    q: "시즌권은 몇 번 가야 본전인가요?",
    a: "표의 본전 횟수는 시즌권 값을 할인 리프트권 1회 값으로 나눠 올림한 횟수예요. 교통비, 렌털비 같은 비용은 넣지 않았어요. 내 결제액과 방문 계획으로 따져 보려면 시즌권 본전 계산기를 써 보세요.",
  },
  {
    q: "가격은 언제 기준인가요?",
    a: `${SEASON_PASS_PRICE_CHECKED_AT}에 리조트 공식 누리집과 판매처에서 조사한 대략값이에요. 시즌권은 판매 차수마다 값이 바뀌어요. 비발디파크는 11월 16일, X6+ 통합권은 11월 19일, 휘닉스 평창은 11월 30일 무렵 다음 차수로 넘어가니 결제 전에 공식 누리집에서 꼭 확인해 주세요.`,
  },
  {
    q: "리프트권 할인가는 누구나 받을 수 있나요?",
    a: "흔히 받는 카드 할인이나 온라인 할인을 적용한 값이에요. 카드사, 결제 수단, 요일에 따라 할인율이 달라서 실제로 내는 값과 차이가 날 수 있어요.",
  },
];

const faqStructuredData = {
  "@context": "https://schema.org",
  "@type": "FAQPage",
  mainEntity: FAQ.map(({ q, a }) => ({ "@type": "Question", name: q, acceptedAnswer: { "@type": "Answer", text: a } })),
};

export default function SeasonPassPricesPage() {
  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(faqStructuredData).replace(/</g, "\u003c") }} />
      <PageHeading
        title="2026/27 스키장 시즌권, 리프트권 가격 비교"
        description="스키장 13곳의 시즌권 값과 리프트권 값을 한 표에 모았어요. 몇 번 가야 시즌권이 이득인지도 함께 볼 수 있어요."
        breadcrumb={<Link href="/tools">← 라이더 도구</Link>}
      />
      <section className="pass-price-intro">
        <p>
          {SEASON_PASS_PRICE_CHECKED_AT}에 공식 누리집과 판매처 자료로 조사한 대략값이에요. 시즌권은 판매 차수에 따라 값이 바뀌고,
          리프트권은 6시간 안팎 평일 권종 기준이에요. 결제 전에는 리조트 공식 누리집에서 다시 확인해 주세요.
        </p>
      </section>
      <p className="pass-price-scroll-hint">표를 옆으로 밀면 리프트권 값과 본전 횟수가 보여요.</p>
      <div className="tool-table-wrap pass-price-table">
        <table>
          <caption>리조트별 시즌권, 리프트권 가격 ({SEASON_PASS_PRICE_CHECKED_AT} 조사)</caption>
          <thead>
            <tr>
              <th scope="col">리조트</th>
              <th scope="col">시즌권</th>
              <th scope="col">리프트권 정가</th>
              <th scope="col">흔한 할인가</th>
              <th scope="col">본전 횟수</th>
              <th scope="col"><span className="sr-only">계산기</span></th>
            </tr>
          </thead>
          <tbody>
            {RESORT_PASS_PRICES.map((resort) => {
              const opening = openingText(resort.code);
              const change = resort.passChange;
              return (
                <tr key={resort.code}>
                  <th scope="row">
                    {resort.name}
                    {opening && <small>{opening}</small>}
                  </th>
                  <td>
                    {resort.passPrice === null ? "확인 안 됨" : won(resort.passPrice)}
                    <small>{change ? `${resort.passLabel}, 11월 15일까지` : resort.passLabel}</small>
                    {change && (
                      <>
                        <span className="pass-price-next">{won(change.price)}</span>
                        <small>{change.label}, 11월 16일부터</small>
                      </>
                    )}
                  </td>
                  <td>{resort.ticketListPrice ? won(resort.ticketListPrice) : "-"}</td>
                  <td>
                    {won(resort.ticketPrice)}
                    <small>{resort.ticketLabel}</small>
                  </td>
                  <td>
                    {resort.passPrice === null ? "-" : `${breakEven(resort.passPrice, resort.ticketPrice)}회`}
                    {change && <small>11월 16일부터 {breakEven(change.price, resort.ticketPrice)}회</small>}
                  </td>
                  <td>
                    <Link href={`/tools/season-pass?resort=${resort.code}`}>계산하기</Link>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="pass-price-cta">
        <p>교통비와 예상 방문 횟수까지 넣어 보면 내 조건의 본전을 알 수 있어요. 표의 리조트 줄에서 계산하기를 누르면 가격이 미리 채워져요.</p>
        <Link href="/tools/season-pass" className="pass-price-cta-link">시즌권 본전 계산기 열기 →</Link>
      </div>
      <section className="tool-explanation">
        <h2>X6+ 통합 시즌권</h2>
        <p>여섯 곳의 리조트에서 함께 쓰는 시즌권이에요.</p>
        <dl className="pass-price-notes">
          <div>
            <dt>X6+ 값을 쓴 곳</dt>
            <dd>
              <ul className="pass-price-chips">
                <li>용평</li>
                <li>웰리힐리</li>
                <li>지산</li>
                <li>엘리시안 강촌</li>
              </ul>
              <span>일반 성인이 따로 살 수 있는 단독 시즌권이 없어 통합권 값을 적었어요.</span>
            </dd>
          </div>
          <div>
            <dt>가격 기준</dt>
            <dd>지금 값은 2차 판매가예요. <strong>11월 19일 이후 바뀔 수 있어요.</strong></dd>
          </div>
          <div>
            <dt>알펜시아</dt>
            <dd>이번 시즌 값이 아직 나오지 않아 지난 시즌 단독 시즌권 값을 적었어요.</dd>
          </div>
          <div>
            <dt>에덴밸리</dt>
            <dd>시즌권 값을 찾지 못했어요.</dd>
          </div>
        </dl>
      </section>
      <section className="tool-explanation">
        <h2>자주 묻는 질문</h2>
        {FAQ.map(({ q, a }) => (
          <details key={q}>
            <summary>{q}</summary>
            <p>{a}</p>
          </details>
        ))}
        <div className="tools-related">
          <Link href="/tools/season-pass">시즌권 본전 계산기 →</Link>
          <Link href="/resort-cam">리조트별 개장일과 슬로프캠 →</Link>
          <Link href="/carpool">카풀과 동행에서 교통비 줄이기 →</Link>
        </div>
      </section>
    </>
  );
}
