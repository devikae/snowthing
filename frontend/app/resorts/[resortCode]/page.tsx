import Link from "next/link";
import { notFound } from "next/navigation";
import { formatCarpoolDateTime } from "../../carpool/format";
import { Footer, TopNav } from "../../components/SiteChrome";
import { isResortReportSeason } from "../../lib/resortReports";
import { kstDateKey } from "../../lib/seasonOpening";
import { SEASON_PASS_PRICE_CHECKED_AT, currentPass } from "../../lib/seasonPassPrices";
import { SITE_URL, publicPageMetadata } from "../../lib/siteMetadata";
import { cameraSlug, resortSlug } from "../../resort-cam/types";
import { formatGradient, formatLength } from "../../slopes/specs";
import { findResort, getOpenCarpools, getOpenings, getSlopeBoard, getSlopeSpecs, getTodayReports, getWeather } from "../data";
import { formatKstStamp, formatKstTime, formatOpeningDate, openingSummary, openingView, passPriceFor, won } from "../format";
import { JoinPrompt } from "../JoinPrompt";
import styles from "../resorts.module.css";

// 요청마다 그린다. 오늘 날짜(D-DAY)가 빌드나 예전 렌더에 굳지 않게 하고, 데이터는 data.ts가 잠깐씩 보관한다.
export const dynamic = "force-dynamic";

const WEATHER_SOURCE = { OFFICIAL: "리조트 공식", KMA: "기상청" } as const;
const CAMERA_LINKS = 8;
const REPORT_ROWS = 5;
const CARPOOL_ROWS = 3;

type Params = { params: Promise<{ resortCode: string }> };

export async function generateMetadata({ params }: Params) {
  const { resort } = await findResort((await params).resortCode);
  if (!resort) notFound();
  const openings = await getOpenings();
  const opening = openingSummary(
    openingView(openings?.resorts.find((item) => item.resortCode === resort.code), kstDateKey(new Date())),
  );
  return publicPageMetadata(
    `${resort.name} 개장일, 날씨, 슬로프 현황, 시즌권 가격`,
    `${resort.regionName} ${resort.name}${opening ? `(${opening})` : ""}의 개장일, 기온, 슬로프캠 ${resort.cameras.length}개, 슬로프 운영 현황, 오늘의 설질 제보, 카풀, 시즌권과 리프트권 가격을 한 화면에서 확인하세요.`,
    `/resorts/${resortSlug(resort.code)}`,
  );
}

export default async function ResortLandingPage({ params }: Params) {
  // 없는 리조트는 여기서 404로 답한다. generateMetadata에서만 부르면 상태 코드가 200으로 나갈 수 있다.
  const { resort, resorts } = await findResort((await params).resortCode);
  if (!resort) notFound();

  const [openings, weather, board, specs, reports, carpools] = await Promise.all([
    getOpenings(),
    getWeather(resort.code),
    getSlopeBoard(),
    getSlopeSpecs(),
    getTodayReports(),
    getOpenCarpools(),
  ]);

  const now = new Date();
  const today = kstDateKey(now);
  const slug = resortSlug(resort.code);
  const path = `/resorts/${slug}`;
  const opening = openingView(openings?.resorts.find((item) => item.resortCode === resort.code), today);
  const slopeStatus = board?.find((item) => item.resortCode === resort.code);
  const spec = specs?.find((item) => item.code === resort.code);
  const resortReports = reports?.filter((item) => item.resortCode === resort.code) ?? null;
  // 출발이 가까운 순, 같은 시각이면 ID 순으로 고정한다.
  const resortCarpools =
    carpools
      ?.filter((item) => item.destinationResortName === resort.name)
      .sort((left, right) => left.departureAt.localeCompare(right.departureAt) || left.publicId.localeCompare(right.publicId)) ?? null;
  const price = passPriceFor(resort.code);
  const pass = price ? currentPass(price, today) : null;
  const reportSeason = isResortReportSeason(now);

  // 우리는 리조트 운영자가 아니다. 이 페이지가 어느 스키장에 관한 것인지만 알린다.
  const structuredData = {
    "@context": "https://schema.org",
    "@type": "WebPage",
    name: `${resort.name} 개장일, 날씨, 슬로프 현황, 시즌권 가격`,
    url: `${SITE_URL}${path}`,
    inLanguage: "ko-KR",
    isPartOf: { "@id": `${SITE_URL}/#website` },
    about: { "@type": "SkiResort", name: resort.name, address: { "@type": "PostalAddress", addressCountry: "KR", addressRegion: resort.regionName } },
    breadcrumb: {
      "@type": "BreadcrumbList",
      itemListElement: [
        { "@type": "ListItem", position: 1, name: "리조트 정보", item: `${SITE_URL}/resorts` },
        { "@type": "ListItem", position: 2, name: resort.name, item: `${SITE_URL}${path}` },
      ],
    },
  };

  return (
    <div className="community-page">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData).replace(/</g, "\\u003c") }} />
      <TopNav />
      <main className="community-container slopes-page">
        <header className={styles.hero}>
          <nav className={styles.breadcrumb} aria-label="위치">
            <Link href="/resorts">리조트 정보</Link> / <span>{resort.name}</span>
          </nav>
          <div className={styles.heroTitle}>
            <span className={styles.region}>{resort.regionName}</span>
            <h1>{resort.name}</h1>
            {opening.kind === "upcoming" && (
              <span className={styles.dday}>
                {opening.dday}
                {opening.confirmed ? null : <small>예상</small>}
              </span>
            )}
          </div>
          <p className={styles.lead}>
            개장일, 기온, 슬로프캠, 슬로프 운영 현황, 오늘의 설질 제보, 카풀, 시즌권과 리프트권 가격을 한 화면에 모았습니다.
          </p>
        </header>

        <div className={styles.grid}>
          <section className={styles.card} aria-labelledby="opening-title">
            <h2 id="opening-title">개장일</h2>
            {opening.kind === "none" ? (
              <p className={styles.empty}>아직 개장일 정보가 없습니다.</p>
            ) : (
              <>
                <p className={styles.figure}>
                  <strong>{formatOpeningDate(opening.date)}</strong>
                  {opening.kind === "upcoming" && (
                    <span className={styles.tag} data-tone={opening.confirmed ? "confirmed" : "estimate"}>
                      {opening.confirmed ? "확정" : "예상"}
                    </span>
                  )}
                </p>
                <p className={styles.note}>
                  {opening.kind === "upcoming" && opening.confirmed && `리조트가 발표한 개장일입니다. 개장까지 ${opening.dday}.`}
                  {opening.kind === "upcoming" && !opening.confirmed && `리조트 발표 전이라 예상일입니다. 예상일 기준 ${opening.dday}.`}
                  {opening.kind === "opened" && "이번 시즌 개장일입니다."}
                  {opening.kind === "passedEstimate" && "예상일이 지났습니다. 실제 개장 여부는 리조트 공지를 확인해 주세요."}
                </p>
              </>
            )}
            <div className={styles.links}>
              <Link href="/resorts">다른 리조트 개장일</Link>
            </div>
          </section>

          <section className={styles.card} aria-labelledby="weather-title">
            <h2 id="weather-title">기온</h2>
            {weather ? (
              <>
                <p className={styles.figure}>
                  <strong>{weather.temperature.toFixed(1)}°C</strong>
                  {weather.sky && <span className={styles.figureSub}>{weather.sky}</span>}
                </p>
                <p className={styles.note}>
                  {formatKstTime(weather.observedAt)} 기준, {WEATHER_SOURCE[weather.source] ?? "관측"} 자료
                  {weather.stale && ". 최근 갱신에 실패해 이전 값을 보여 줍니다"}
                </p>
              </>
            ) : (
              <p className={styles.empty}>지금은 기온 정보를 받지 못했습니다.</p>
            )}
          </section>

          <section className={styles.card} aria-labelledby="slope-title">
            <h2 id="slope-title">슬로프 운영 현황</h2>
            {!board ? (
              <p className={styles.empty}>운영 현황을 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</p>
            ) : !slopeStatus?.supported ? (
              <p className={styles.empty}>공식 사이트에서 현황을 받을 수 없어 아직 보여 드리지 못합니다.</p>
            ) : !slopeStatus.collectedAt ? (
              <p className={styles.empty}>
                {opening.kind === "upcoming" ? "시즌 개장 전입니다. " : ""}아직 받은 현황이 없습니다.
              </p>
            ) : (
              <>
                <p className={styles.figure}>
                  <strong data-open={(slopeStatus.openSlopeCount ?? 0) > 0}>{slopeStatus.openSlopeCount ?? 0}</strong>
                  <span className={styles.figureSub}>
                    / {slopeStatus.totalSlopeCount ?? slopeStatus.slopes.length}면 운영
                  </span>
                </p>
                <p className={styles.note}>
                  {slopeStatus.operatingNote ? `운영 시간: ${slopeStatus.operatingNote}. ` : ""}
                  {formatKstStamp(slopeStatus.collectedAt)} 갱신
                  {slopeStatus.stale && ". 최근 갱신에 실패해 이전 값을 보여 줍니다"}
                </p>
              </>
            )}
            {spec && (
              <dl className={styles.facts}>
                <div><dt>슬로프</dt><dd>{spec.slopeCount}개</dd></div>
                <div><dt>가장 긴 슬로프</dt><dd>{formatLength(spec.longestLengthM)}</dd></div>
                <div><dt>최대 경사</dt><dd>{formatGradient(spec.steepestMaxGradient)}</dd></div>
              </dl>
            )}
            <div className={styles.links}>
              <Link href={`/slopes#${slug}`}>슬로프 현황 보기</Link>
              {spec && <Link href={`/slopes/${slug}`}>슬로프 제원과 코스 맵</Link>}
              {slopeStatus?.sourceUrl && (
                <a href={slopeStatus.sourceUrl} target="_blank" rel="noopener noreferrer">공식 페이지</a>
              )}
            </div>
          </section>

          <section className={styles.card} aria-labelledby="cam-title">
            <h2 id="cam-title">슬로프캠</h2>
            {resort.cameras.length === 0 ? (
              <p className={styles.empty}>등록된 슬로프캠이 없습니다.</p>
            ) : (
              <>
                <p className={styles.note}>리조트가 제공하는 카메라 {resort.cameras.length}개를 연결해 두었습니다.</p>
                <ul className={styles.chips}>
                  {resort.cameras.slice(0, CAMERA_LINKS).map((camera) => (
                    <li key={camera.code}>
                      <Link href={`/resort-cam/${slug}/${cameraSlug(camera.code)}`}>{camera.name}</Link>
                    </li>
                  ))}
                  {resort.cameras.length > CAMERA_LINKS && <li className={styles.chipMore}>외 {resort.cameras.length - CAMERA_LINKS}개</li>}
                </ul>
              </>
            )}
            <div className={styles.links}>
              <Link href={`/resort-cam/${slug}`}>{resort.name} 슬로프캠 보기</Link>
            </div>
          </section>

          <section className={styles.card} aria-labelledby="report-title">
            <h2 id="report-title">오늘의 설질</h2>
            {!reportSeason ? (
              <p className={styles.empty}>설질 제보는 10월부터 4월까지 받습니다.</p>
            ) : !resortReports ? (
              <p className={styles.empty}>설질 제보를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</p>
            ) : resortReports.length === 0 ? (
              <p className={styles.empty}>오늘 등록된 {resort.name} 설질 제보가 아직 없습니다.</p>
            ) : (
              <ul className={styles.rows}>
                {resortReports.slice(0, REPORT_ROWS).map((report) => (
                  <li key={report.reportId}>
                    <strong>{report.content}</strong>
                    <span>
                      {report.authorNickname}, {formatKstTime(report.createdAt)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <div className={styles.links}>
              <Link href="/resort-reports">오늘의 설질 전체 보기</Link>
              {reportSeason && <Link href="/">홈에서 제보 남기기</Link>}
            </div>
          </section>

          <section className={styles.card} aria-labelledby="carpool-title">
            <h2 id="carpool-title">카풀과 동행</h2>
            {!resortCarpools ? (
              <p className={styles.empty}>카풀 모집글을 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</p>
            ) : resortCarpools.length === 0 ? (
              <p className={styles.empty}>지금 모집 중인 {resort.name} 카풀이 없습니다.</p>
            ) : (
              <ul className={styles.rows}>
                {resortCarpools.slice(0, CARPOOL_ROWS).map((carpool) => (
                  <li key={carpool.publicId}>
                    <Link href={`/carpool/${carpool.publicId}`}>
                      <strong>{carpool.title}</strong>
                    </Link>
                    <span>
                      {formatCarpoolDateTime(carpool.departureAt)} 출발, {carpool.departureRegion}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <div className={styles.links}>
              <Link href="/carpool">카풀 모집글 전체 보기</Link>
              <Link href="/carpool/new">카풀 모집글 쓰기</Link>
            </div>
          </section>

          <section className={`${styles.card} ${styles.wide}`} aria-labelledby="price-title">
            <h2 id="price-title">시즌권, 리프트권 가격</h2>
            {!price || !pass ? (
              <p className={styles.empty}>아직 가격 정보가 없습니다.</p>
            ) : (
              <>
                <dl className={styles.prices}>
                  <div>
                    <dt>시즌권</dt>
                    <dd>
                      <strong>{pass.price === null ? "확인 안 됨" : won(pass.price)}</strong>
                      <small>{pass.label}</small>
                    </dd>
                  </div>
                  <div>
                    <dt>리프트권 할인가</dt>
                    <dd>
                      <strong>{won(price.ticketPrice)}</strong>
                      <small>{price.ticketLabel}</small>
                    </dd>
                  </div>
                </dl>
                <p className={styles.note}>
                  {SEASON_PASS_PRICE_CHECKED_AT}에 공식 누리집과 판매처 자료로 조사한 대략값입니다. 판매 차수와 결제 수단에 따라 달라지니 결제 전에
                  공식 누리집에서 확인해 주세요.
                </p>
              </>
            )}
            <div className={styles.links}>
              <Link href="/tools/season-pass-prices">스키장 13곳 가격 비교</Link>
              {price && <Link href={`/tools/season-pass?resort=${price.code}`}>시즌권 본전 계산하기</Link>}
            </div>
          </section>
        </div>

        <JoinPrompt />

        <nav className={styles.others} aria-label="다른 리조트">
          <h2>다른 리조트</h2>
          <ul className={styles.chips}>
            {resorts
              .filter((item) => item.code !== resort.code)
              .map((item) => (
                <li key={item.code}>
                  <Link href={`/resorts/${resortSlug(item.code)}`}>{item.name}</Link>
                </li>
              ))}
          </ul>
        </nav>
      </main>
      <Footer />
    </div>
  );
}
