"use client";

import Link from "next/link";
import Image from "next/image";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Footer, TopNav } from "./components/SiteChrome";
import LiveChatSection, { MemberProfile } from "./components/LiveChatSection";
import { CompactSelect } from "./components/CompactSelect";
import { OpeningDdayBanner } from "./components/OpeningDdayBanner";
import { PostImageIcon } from "./components/PostImageIcon";
import { PostListAuthor } from "./components/PostListAuthor";
import { API_ENDPOINTS, OffsetPage, ResortReportItem } from "./lib/api";
import { fetchViewer } from "./lib/viewer";
import { RESORT_MAP } from "./lib/resortTags";
import { formatKoreanCalendarDate, formatReportTime, getResortReportDisplayName, isResortReportSeason } from "./lib/resortReports";
import { csrfFetch } from "./lib/csrfFetch";
import { IDEMPOTENCY_HEADER, useIdempotencyKey } from "./lib/idempotencyKey";
import { formatCompactTime, formatShortDateTime } from "./lib/koreanDate";

interface HomePost {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  writerNickname: string;
  writerProfileImageUrl?: string | null;
  writerPublicId?: string | null;
  thumbnailImageUrl: string | null;
  hasImage: boolean;
  viewCount: number;
  commentCount: number;
  createdAt: string;
}

type HomeFeedKey = "all" | "notice" | "free" | "anonymous" | "qna" | "food" | "crew" | "season_room";

interface HomeFeedTab {
  key: HomeFeedKey;
  label: string;
  moreHref: string;
  query: Record<string, string>;
}

const HOME_FEED_TABS: HomeFeedTab[] = [
  { key: "all", label: "전체글", moreHref: "/posts", query: {} },
  { key: "notice", label: "공지사항", moreHref: "/posts?category=NOTICE", query: { categoryCode: "NOTICE" } },
  { key: "free", label: "자유게시판", moreHref: "/posts?category=FREE", query: { categoryCode: "FREE" } },
  { key: "anonymous", label: "익명게시판", moreHref: "/posts?category=ANONYMOUS", query: { categoryCode: "ANONYMOUS" } },
  { key: "qna", label: "질문게시판", moreHref: "/posts?category=QNA", query: { categoryCode: "QNA" } },
  { key: "food", label: "리조트 맛집", moreHref: "/posts?category=FOOD", query: { categoryCode: "FOOD" } },
  { key: "crew", label: "동호회 모집", moreHref: "/posts?category=CREW", query: { categoryCode: "CREW" } },
  { key: "season_room", label: "시즌방 모집", moreHref: "/posts?category=SEASON_ROOM", query: { categoryCode: "SEASON_ROOM" } },
];

const emptyFeeds: Record<HomeFeedKey, HomePost[]> = {
  all: [],
  notice: [],
  free: [],
  anonymous: [],
  qna: [],
  food: [],
  crew: [],
  season_room: [],
};
const emptyFeedErrors: Record<HomeFeedKey, boolean> = {
  all: false,
  notice: false,
  free: false,
  anonymous: false,
  qna: false,
  food: false,
  crew: false,
  season_room: false,
};

interface HomeCarpoolItem {
  publicId: string;
  departureRegion: string;
  destinationResortName: string;
  departureAt: string;
  passengerCapacity: number;
  estimatedCostPerPerson: number;
  equipmentLoadAvailable: boolean;
}

interface MarketPreviewItem {
  publicId: string;
  thumbnailImageUrl: string | null;
}


function formatShortCategory(categoryCode: string, categoryName: string): string {
  switch (categoryCode) {
    case "NOTICE":
      return "공지사항";
    case "FREE":
      return "자유";
    case "ANONYMOUS":
      return "익명";
    case "QNA":
      return "질문";
    case "FOOD":
      return "맛집";
    case "CREW":
      return "동호회";
    case "SEASON_ROOM":
      return "시즌방";
    default:
      return categoryName.replace(/게시판|\s+/g, "");
  }
}


export default function HomePageClient() {
  const router = useRouter();
  const [profile, setProfile] = useState<MemberProfile | null>(null);
  const [activeFeed, setActiveFeed] = useState<HomeFeedKey>("all");
  const [feeds, setFeeds] = useState<Record<HomeFeedKey, HomePost[]>>(emptyFeeds);
  const [feedErrors, setFeedErrors] = useState<Record<HomeFeedKey, boolean>>(emptyFeedErrors);
  // 탭마다 처음 열 때 한 번만 요청한다. 홈 첫 화면은 "전체" 탭만 받는다.
  const [feedLoading, setFeedLoading] = useState<Record<HomeFeedKey, boolean>>(() => ({ ...emptyFeedErrors, all: true }));
  const requestedFeeds = useRef(new Set<HomeFeedKey>());
  const [marketPreviews, setMarketPreviews] = useState<MarketPreviewItem[]>([]);

  const [carpools, setCarpools] = useState<HomeCarpoolItem[]>([]);
  const [snowReportResort, setSnowReportResort] = useState("1");
  const [snowReportContent, setSnowReportContent] = useState("");
  const [todayReports, setTodayReports] = useState<ResortReportItem[]>([]);
  const [resortMasterList, setResortMasterList] = useState<{ id: number; name: string }[]>([]);
  const [resortMasterError, setResortMasterError] = useState(false);
  const [submittingReport, setSubmittingReport] = useState(false);
  const isSubmittingReportRef = useRef(false);
  const reportIdempotencyKey = useIdempotencyKey();
  const [koreanToday, setKoreanToday] = useState(() => formatKoreanCalendarDate(new Date()));
  const reportSeasonOpen = isResortReportSeason(new Date());
  const activeFeedConfig = HOME_FEED_TABS.find((tab) => tab.key === activeFeed) ?? HOME_FEED_TABS[0];
  // 공지는 관리자만 쓰므로 일반 글쓰기로 보내고, 나머지 탭은 해당 게시판을 미리 골라 둔다.
  const writeCategory = activeFeedConfig.query.categoryCode;
  const writeHref = writeCategory && writeCategory !== "NOTICE" ? `/posts/create?category=${writeCategory}` : "/posts/create";

  const loadTodayReports = async () => {
    try {
      const response = await fetch(API_ENDPOINTS.resortReports.today(undefined, 1, 10), { credentials: "include" });
      if (response.ok) {
        const data: OffsetPage<ResortReportItem> = await response.json();
        setTodayReports(Array.isArray(data.content) ? data.content : []);
      }
    } catch {
      setTodayReports([]);
    }
  };

  useEffect(() => {
    void fetch(API_ENDPOINTS.resortReports.today(undefined, 1, 10), { credentials: "include" })
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then((data: OffsetPage<ResortReportItem>) => setTodayReports(Array.isArray(data.content) ? data.content : []))
      .catch(() => setTodayReports([]));
  }, []);

  useEffect(() => {
    void fetch(API_ENDPOINTS.master.resorts, { credentials: "include" })
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((data: { id: number; name: string }[]) => {
        if (Array.isArray(data) && data.length > 0) {
          setResortMasterList(data);
          setSnowReportResort(String(data[0].id));
          setResortMasterError(false);
        } else {
          setResortMasterError(true);
        }
      })
      .catch(() => setResortMasterError(true));
  }, []);

  const handleSnowReportSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!snowReportContent.trim() || submittingReport || isSubmittingReportRef.current) return;
    if (!reportSeasonOpen) {
      alert("설질 제보는 10월부터 다음 해 4월까지만 등록할 수 있습니다.");
      return;
    }
    if (!profile) {
      router.push("/login");
      return;
    }
    const resortId = Number(snowReportResort);
    if (!resortId) {
      alert("리조트를 선택해 주세요.");
      return;
    }

    isSubmittingReportRef.current = true;
    setSubmittingReport(true);
    try {
      const body = JSON.stringify({
        resortId,
        content: snowReportContent.trim(),
      });
      const res = await csrfFetch(API_ENDPOINTS.resortReports.create, {
        method: "POST",
        headers: { "Content-Type": "application/json", [IDEMPOTENCY_HEADER]: reportIdempotencyKey.keyFor(body) },
        body,
      });
      if (res.ok) {
        reportIdempotencyKey.reset();
        setSnowReportContent("");
        await loadTodayReports();
      } else {
        const data = await res.json().catch(() => ({}));
        alert(data.message || "설질 제보를 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }
    } catch {
      alert("서버에 연결하지 못했습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.");
    } finally {
      isSubmittingReportRef.current = false;
      setSubmittingReport(false);
    }
  };

  useEffect(() => {
    void fetchViewer().then(setProfile);
  }, []);

  useEffect(() => {
    void fetch(`${API_ENDPOINTS.carpool.list}?page=0&size=4`, { credentials: "include" })
      .then((response) => (response.ok ? response.json() : Promise.reject()))
      .then((data) => setCarpools(Array.isArray(data.content) ? data.content : []))
      .catch(() => setCarpools([]));
  }, []);

  useEffect(() => {
    void (async () => {
      try {
        const response = await fetch(API_ENDPOINTS.market.preview, { credentials: "include" });
        if (!response.ok) return;
        const data: { items?: MarketPreviewItem[] } = await response.json();
        setMarketPreviews(Array.isArray(data.items) ? data.items.slice(0, 4) : []);
      } catch {
        setMarketPreviews([]);
      }
    })();
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setKoreanToday(formatKoreanCalendarDate(new Date())), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  const loadFeed = useCallback(async (tab: HomeFeedTab) => {
    requestedFeeds.current.add(tab.key);
    setFeedErrors((current) => ({ ...current, [tab.key]: false }));
    setFeedLoading((current) => ({ ...current, [tab.key]: true }));
    const params = new URLSearchParams({ page: "1", size: "15", ...tab.query });
    try {
      const response = await fetch(`${API_ENDPOINTS.posts.list}?${params}`, { credentials: "include" });
      if (!response.ok) throw new Error(`${tab.key} 게시글 목록 응답 오류: ${response.status}`);
      const data = await response.json();
      setFeeds((current) => ({ ...current, [tab.key]: Array.isArray(data.content) ? data.content.slice(0, 15) : [] }));
    } catch {
      setFeedErrors((current) => ({ ...current, [tab.key]: true }));
    } finally {
      setFeedLoading((current) => ({ ...current, [tab.key]: false }));
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadFeed(HOME_FEED_TABS[0]), 0);
    return () => window.clearTimeout(timer);
  }, [loadFeed]);

  const selectFeed = (tab: HomeFeedTab) => {
    setActiveFeed(tab.key);
    if (!requestedFeeds.current.has(tab.key)) void loadFeed(tab);
  };

  return (
    <div className="community-page">
      <TopNav active="home" />
      <main className="community-container home-main">
        <OpeningDdayBanner />
        {/* TIER 1: Left Board Feed + Right Live Chat */}
        <div className="home-tier-1">
          <section className="home-feed tier-1-left">
            <div className="post-board">
              <h1 className="sr-only">눈팅 SnowThing</h1>
              <nav className="board-tabs" aria-label="홈 게시판 선택">
                <div className="filter-pills">
                  {HOME_FEED_TABS.map((tab) => (
                    <button
                      key={tab.key}
                      type="button"
                      className={activeFeed === tab.key ? "active" : ""}
                      onClick={() => selectFeed(tab)}
                    >
                      {tab.label}
                    </button>
                  ))}
                </div>
                <div className="board-tabs-actions">
                  <Link href={activeFeedConfig.moreHref} className="board-tabs-more">
                    더보기 →
                  </Link>
                  <Link href={writeHref} className="board-tabs-write">
                    <span className="material-symbols-outlined" aria-hidden="true">edit</span>
                    글쓰기
                  </Link>
                </div>
              </nav>

              <div className="compact-post-list">
                {feedLoading[activeFeed] ? (
                  <div className="home-feed-state">
                    <span className="material-symbols-outlined spin">progress_activity</span>
                    <strong>게시글을 불러오는 중입니다.</strong>
                  </div>
                ) : feedErrors[activeFeed] ? (
                  <div className="home-feed-state error">
                    <span className="material-symbols-outlined">cloud_off</span>
                    <strong>게시글을 불러오지 못했습니다.</strong>
                    <button type="button" onClick={() => void loadFeed(activeFeedConfig)}>
                      다시 시도
                    </button>
                  </div>
                ) : feeds[activeFeed].length === 0 ? (
                  <div className="home-feed-state">
                    <span className="material-symbols-outlined">edit_note</span>
                    <strong>등록된 게시글이 없습니다.</strong>
                  </div>
                ) : (
                  feeds[activeFeed].map((post) => {
                    return (
                      <Link href={`/posts/${post.publicId}`} className="compact-post m-row" key={post.publicId}>
                        <div className="m-row-left">
                          {activeFeed === "all" && (
                            <span className={`m-cat ${post.categoryCode === "NOTICE" ? "notice" : post.categoryCode === "QNA" ? "accent" : ""}`}>
                              {formatShortCategory(post.categoryCode, post.categoryName)}
                            </span>
                          )}
                          <strong className="m-title">{post.title}</strong>
                          {post.hasImage && <PostImageIcon />}
                        </div>
                        <div className="m-row-right">
                          <PostListAuthor nickname={post.writerNickname} profileImageUrl={post.writerProfileImageUrl} publicId={post.writerPublicId} />
                          <span className="m-stats">
                            {post.commentCount > 0 ? (
                              <span className="m-stat m-stat-comment hot" title={`댓글 ${post.commentCount}`}>
                                <span className="material-symbols-outlined" aria-hidden="true">
                                  chat_bubble
                                </span>
                                {post.commentCount}
                              </span>
                            ) : (
                              <span className="m-stat m-stat-comment is-empty" aria-hidden="true" />
                            )}
                            <span className="m-stat m-stat-view" title={`조회 ${post.viewCount.toLocaleString()}`}>
                              <span className="material-symbols-outlined" aria-hidden="true">
                                visibility
                              </span>
                              {post.viewCount.toLocaleString()}
                            </span>
                          </span>
                          <time className="m-time">{formatCompactTime(post.createdAt)}</time>
                        </div>
                      </Link>
                    );
                  })
                )}
              </div>
            </div>
          </section>

          <div className="home-live-chat tier-1-right">
            <LiveChatSection currentMember={profile} />
          </div>
        </div>

        {/* TIER 2: Full-Width Wide Snow Report Section */}
        <section className="snow-report-widget home-tier-2">
          <div className="section-head">
            <div className="section-head-title">
              <small className="section-eyebrow">라이더 현장 제보</small>
              <div className="section-title-inline">
                <h2>오늘의 설질</h2>
                <time className="snow-date-badge">{koreanToday}</time>
              </div>
            </div>
            <div className="section-actions">
              <Link href="/resort-reports" className="board-tabs-more">
                전체 제보 보기 →
              </Link>
            </div>
          </div>

          <form className="snow-report-compose unified-capsule-form" onSubmit={handleSnowReportSubmit}>
            <CompactSelect
              value={snowReportResort}
              options={resortMasterList.map((resort) => ({
                value: String(resort.id),
                label: getResortReportDisplayName(resort.name),
              }))}
              onChange={setSnowReportResort}
              ariaLabel="리조트 선택"
              disabled={resortMasterError || resortMasterList.length === 0 || !reportSeasonOpen}
            />
            <input
              value={snowReportContent}
              onChange={(event) => setSnowReportContent(event.target.value)}
              onClick={() => {
                if (!profile) {
                  router.push("/login");
                }
              }}
              readOnly={!profile}
              maxLength={100}
              placeholder={
                !reportSeasonOpen
                  ? "설질 제보는 10월부터 4월까지 등록할 수 있습니다"
                  : profile
                    ? "지금 슬로프 설질·대기시간·정설 상태를 한 줄(100자 이내)로 공유해주세요..."
                    : "로그인하고 오늘의 설질을 제보해보세요"
              }
              aria-label="설질 제보 내용"
              className={!profile ? "cursor-pointer" : undefined}
              disabled={!reportSeasonOpen || resortMasterError}
            />
            <button
              type="submit"
              className="capsule-send-btn"
              disabled={profile ? (!snowReportContent.trim() || submittingReport || !reportSeasonOpen || resortMasterError || resortMasterList.length === 0) : false}
            >
              {profile ? (submittingReport ? "등록중" : "제보 등록") : "로그인"}
            </button>
          </form>

          {resortMasterError && (
            <p className="snow-report-empty">리조트 목록을 불러오지 못해 제보를 등록할 수 없습니다.</p>
          )}

          <div className="snow-report-list snow-wide-list">
            {todayReports.length === 0 ? (
              <div className="snow-empty-state">
                <div className="snow-empty-copy">
                  <strong>오늘 등록된 설질 제보가 아직 없습니다</strong>
                  <span>위 입력창에서 리조트를 선택하고 첫 번째 현장 설질·대기열 소식을 공유해 보세요.</span>
                </div>
              </div>
            ) : (
              todayReports.map((report) => {
                const resort = RESORT_MAP[report.resortCode];
                return (
                  <article className="snow-report-row snow-wide-row" key={report.reportId}>
                    <span className={`snow-report-tag ${resort?.markerClass ?? "bg-[#3f6f8f]"}`}>
                      {getResortReportDisplayName(report.resortName)}
                    </span>
                    <strong className="snow-report-content" title={report.content}>
                      {report.content}
                    </strong>
                    <span className="snow-report-author">{report.authorNickname}</span>
                    <time className="snow-report-time">{formatReportTime(report.createdAt)}</time>
                  </article>
                );
              })
            )}
          </div>
        </section>

        {/* TIER 3: 50:50 Carpool & Used Market */}
        <div className="home-tier-3">
          <section className="carpool-widget tier-3-left" id="carpool">
            <div className="section-head">
              <div className="section-head-title">
                <small className="section-eyebrow">리조트 같이 가기</small>
                <h2>카풀과 동행</h2>
              </div>
              <div className="section-actions">
                <Link href="/carpool/new" className="btn-outline-pill">
                  + 모집 등록
                </Link>
                <Link href="/carpool" className="board-tabs-more">
                  전체 보기 →
                </Link>
              </div>
            </div>

            <div className="carpool-compact-list">
              {carpools.length === 0 ? (
                <div className="carpool-widget-empty">
                  <span className="material-symbols-outlined" aria-hidden="true">
                    directions_car
                  </span>
                  <p>등록된 카풀 모집글이 없습니다.</p>
                </div>
              ) : (
                carpools.map((item) => (
                  <Link href={`/carpool/${item.publicId}`} className="carpool-row" key={item.publicId}>
                    <div className="cp-left">
                      <span className={`cp-equip ${item.equipmentLoadAvailable ? "ok" : "no"}`}>
                        {item.equipmentLoadAvailable ? "장비가능" : "장비불가"}
                      </span>
                      <div className="cp-route">
                        <b>{item.departureRegion}</b>
                        <span className="arr">→</span>
                        <em>{item.destinationResortName}</em>
                      </div>
                      <span className="cp-date">{item.departureAt ? formatShortDateTime(item.departureAt) : ""}</span>
                    </div>
                    <div className="cp-right">
                      <span className="cp-seat">{item.passengerCapacity}명</span>
                      <strong className="cp-price">{item.estimatedCostPerPerson.toLocaleString("ko-KR")}원</strong>
                    </div>
                  </Link>
                ))
              )}
            </div>
          </section>

          <section className="market-widget tier-3-right" id="market">
            <div className="section-head">
              <div className="section-head-title">
                <small className="section-eyebrow">중고 장비 거래</small>
                <h2>중고장터</h2>
              </div>
              <div className="section-actions">
                <Link href="/market" className="board-tabs-more">
                  장터 바로가기 →
                </Link>
              </div>
            </div>

            {marketPreviews.length > 0 ? (
              <div className="market-grid market-preview-grid market-lookbook-4">
                {marketPreviews.map((item) => {
                  const detailHref = `/market/${item.publicId}`;
                  const href = profile ? detailHref : `/login?returnUrl=${encodeURIComponent(detailHref)}`;
                  return (
                    <Link href={href} key={item.publicId} className="lookbook-item" aria-label="중고장터 판매글 보기">
                      <div className="lookbook-media">
                        {item.thumbnailImageUrl ? (
                          <Image src={item.thumbnailImageUrl} alt="" width={240} height={192} unoptimized />
                        ) : (
                          <span className="market-image-placeholder material-symbols-outlined">inventory_2</span>
                        )}
                        <div className="lookbook-glass">
                          <span>매물 보기</span>
                          <i aria-hidden="true">→</i>
                        </div>
                      </div>
                    </Link>
                  );
                })}
              </div>
            ) : (
              <div className="market-preview-empty">
                <span className="material-symbols-outlined">inventory_2</span>
                <p>등록된 판매 상품이 없습니다.</p>
              </div>
            )}
          </section>
        </div>
      </main>
      <Footer />
    </div>
  );
}
