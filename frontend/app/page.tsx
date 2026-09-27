"use client";

import Link from "next/link";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { Footer, TopNav } from "./components/SiteChrome";
import LiveChatSection, { MemberProfile } from "./components/LiveChatSection";
import { API_ENDPOINTS } from "./lib/api";
import { RESORT_MAP, RESORT_OPTIONS } from "./lib/resortTags";
import { formatKoreanCalendarDate, RESORT_REPORT_PREVIEWS } from "./lib/resortReports";

const HERO_IMAGES = [
  "https://lh3.googleusercontent.com/aida/AEtjO1UbsQy3vUnB80P1wDDnbGosvZS9vqvYFfKYsbt-ATgRpqmc2zAPzC52mv7kE-dFt3s-FEwC34VCTJRYlYi_Rv20X4gbV1Ot4EXHI4_0yNB7xgvC-4jj_0S5zRoyhDgx6tmmOf3WlnzXxe1_njPrVcsEQvpsjpP-uLoumLkQrGk_Sl87eNShpSVr4YpqH1lzrGDTFYJBa1ek0ZngAq1VNj9Hp9K8uVOjTkHDEFp6cfh7IlqT2pMxpgODiMr9",
  "https://lh3.googleusercontent.com/aida/AEtjO1Vcno6No205vArthV-VYm_1xWKA9tsOEYUO3gVlGWnI5gZTow2ELVmgfr8pg185_aUMpvVZ8e4z4F1PbMyRxb3M7hGNaZtOc5set_3eOhXq7bWRfEt2wrA2p8NYhZJHoVinT-4qax2j-zrOhbTWQ0yXmg6rzznW_92J_zQJ-rcSAncKtYkzAsWdayvEpFAYDbpp_Q2WsQeagnmchLIQKsY5uzWyAbfa4iaRW8TVldr_G1j_UNfHRcspz22v",
];

const resorts = [
  { name: "휘닉스 평창", status: "정상운영", temp: "-4.5°C", open: "12 / 18면", detail: "야간 8", snow: "+4cm (압설 양호)", crowd: "쾌적 (대기 3분)", slopes: "펭귄/챔피언 슬로프", tone: "good" },
  { name: "비발디파크", status: "정상운영", temp: "-2.1°C", open: "9 / 12면", detail: "새벽운영", snow: "강설 (하단 아이스 약간)", crowd: "보통 (초급 혼잡)", slopes: "발라드/테크노", tone: "warn" },
  { name: "하이원 리조트", status: "전면개방", temp: "-6.8°C", open: "18 / 18면", detail: "전코스", snow: "+6cm (극상 파우더)", crowd: "쾌적 (대기 없음)", slopes: "마운틴탑/아테나", tone: "good" },
  { name: "모나 용평", status: "정상운영", temp: "-5.2°C", open: "22 / 28면", detail: "", snow: "강설 하드팩 (엣징 양호)", crowd: "쾌적 (레인보우 여유)", slopes: "레드/골드/레인보우", tone: "good" },
  { name: "웰리힐리파크", status: "정상운영", temp: "-5.0°C", open: "14 / 19면", detail: "", snow: "압설 (C3 모글밭 주의)", crowd: "쾌적 (대기 2분)", slopes: "에코/챌린지", tone: "good" },
  { name: "지산 포레스트", status: "야간운영", temp: "-1.5°C", open: "6 / 7면", detail: "", snow: "인공설 압설 (슬러시 약간)", crowd: "혼잡 (대기 10분)", slopes: "1/2/3번 슬로프", tone: "busy" },
];

interface HomePost {
  publicId: string;
  categoryName: string;
  categoryCode: string;
  title: string;
  writerNickname: string;
  thumbnailImageUrl: string | null;
  hasImage: boolean;
  viewCount: number;
  commentCount: number;
  createdAt: string;
}

type HomeFeedKey = "all" | "popular" | "free" | "anonymous" | "gear";

interface HomeFeedTab {
  key: HomeFeedKey;
  label: string;
  moreHref: string;
  query: Record<string, string>;
}

const HOME_FEED_TABS: HomeFeedTab[] = [
  { key: "all", label: "전체글", moreHref: "/posts", query: {} },
  { key: "popular", label: "실시간 베스트", moreHref: "/posts?view=best", query: { viewType: "BEST" } },
  { key: "free", label: "자유게시판", moreHref: "/posts?category=FREE", query: { categoryCode: "FREE" } },
  { key: "anonymous", label: "익명게시판", moreHref: "/posts?category=ANONYMOUS", query: { categoryCode: "ANONYMOUS" } },
  { key: "gear", label: "장비 후기", moreHref: "/posts?category=QNA", query: { categoryCode: "QNA" } },
];

const emptyFeeds: Record<HomeFeedKey, HomePost[]> = { all: [], popular: [], free: [], anonymous: [], gear: [] };
const emptyFeedErrors: Record<HomeFeedKey, boolean> = { all: false, popular: false, free: false, anonymous: false, gear: false };

const carpools = [
  { from: "서울 사당", to: "하이원", date: "12/28(토) 05:00 출발", price: "기름/톨비 N빵", seat: "2석 남음", note: "루프박스 데크 4장 적재 가능 · 비흡연" },
  { from: "경기 분당(서현)", to: "휘닉스파크", date: "12/28(토) 18:00 야간", price: "편도 1.5만원", seat: "1석 남음", note: "SUV 4륜 운행 · 장비 실어드립니다" },
];

const gearItems = [
  { title: "버튼 스텝온 포토 270", price: "220,000원", note: "휘팍 직거래 가능", image: "https://lh3.googleusercontent.com/aida-public/AB6AXuBWec9rQzVJ3kYEXI-fsoR63FF7wuSFgqNUS8T0VmDNiZ8-QjTufr_5psAgEV_2uHBFUeRwSn6wvt-RapWGnaW3xcep_v7VAT0xpFiwRV1Jjof4wgDQBk2cO1e_5qbwpISaipedws-0etM9Ve3aDtjwvEJLve_GiH_maY3rfjkyLqNO3GH2hmLV5yYpFoHsZMdZYl2AYZl0XizOc_0oluIr5SIoX34Rg0BUruWSAPc_itY7_kEvwhJwYw" },
  { title: "스미스 4D MAG 고글", price: "180,000원", note: "렌즈 2종 · 상태 S급", image: "https://lh3.googleusercontent.com/aida-public/AB6AXuDRFUeNNwG_jiLzZL8yhifJtFTGjEduHPIXrXgOtSXheWa1zJRqPTOsMmqSwMN5SwLCE9ObUY4CIcE8ZeodhrmbwAzIK2WBoxo7RZIcI2lqj-GeL2msYYbGTBv4R0skSaz9TL0ZippD8Aw4j_aOdVYd4ppFqmQe258KGppZR0DGxvTSNS0wrnh_-Yoc0t15VRs36PZCydGyPl56-ky_C8vjPtH39jzaKEQYDMiSBD3pBMMadCkT48BxSQ" },
];

export default function HomePage() {
  const [hero, setHero] = useState(0);
  const [profile, setProfile] = useState<MemberProfile | null>(null);
  const [activeFeed, setActiveFeed] = useState<HomeFeedKey>("all");
  const [feeds, setFeeds] = useState<Record<HomeFeedKey, HomePost[]>>(emptyFeeds);
  const [feedErrors, setFeedErrors] = useState<Record<HomeFeedKey, boolean>>(emptyFeedErrors);
  const [feedsLoading, setFeedsLoading] = useState(true);
  const [snowReportResort, setSnowReportResort] = useState("PHOENIX");
  const [snowReportContent, setSnowReportContent] = useState("");
  const [koreanToday, setKoreanToday] = useState(() => formatKoreanCalendarDate(new Date()));
  const resortRef = useRef<HTMLDivElement>(null);
  const activeFeedConfig = HOME_FEED_TABS.find((tab) => tab.key === activeFeed) ?? HOME_FEED_TABS[0];

  useEffect(() => {
    void (async () => {
      try {
        const response = await fetch(API_ENDPOINTS.members.me, { credentials: "include" });
        setProfile(response.ok ? await response.json() : null);
      } catch {
        setProfile(null);
      }
    })();
  }, []);

  useEffect(() => {
    let cancelled = false;

    void (async () => {
      const results = await Promise.allSettled(
        HOME_FEED_TABS.map(async (tab) => {
          const params = new URLSearchParams({ page: "1", size: "10", ...tab.query });
          const response = await fetch(`${API_ENDPOINTS.posts.list}?${params}`, { credentials: "include" });
          if (!response.ok) throw new Error(`${tab.key} 게시글 목록 응답 오류: ${response.status}`);
          const data = await response.json();
          return { key: tab.key, posts: Array.isArray(data.content) ? data.content.slice(0, 10) : [] };
        }),
      );

      if (cancelled) return;

      const nextFeeds = { ...emptyFeeds };
      const nextErrors = { ...emptyFeedErrors };
      results.forEach((result, index) => {
        const key = HOME_FEED_TABS[index].key;
        if (result.status === "fulfilled") nextFeeds[key] = result.value.posts;
        else nextErrors[key] = true;
      });
      setFeeds(nextFeeds);
      setFeedErrors(nextErrors);
      setFeedsLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setKoreanToday(formatKoreanCalendarDate(new Date())), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  const retryFeed = async (tab: HomeFeedTab) => {
    setFeedErrors((current) => ({ ...current, [tab.key]: false }));
    const params = new URLSearchParams({ page: "1", size: "10", ...tab.query });
    try {
      const response = await fetch(`${API_ENDPOINTS.posts.list}?${params}`, { credentials: "include" });
      if (!response.ok) throw new Error(`${tab.key} 게시글 목록 응답 오류: ${response.status}`);
      const data = await response.json();
      setFeeds((current) => ({ ...current, [tab.key]: Array.isArray(data.content) ? data.content.slice(0, 10) : [] }));
    } catch {
      setFeedErrors((current) => ({ ...current, [tab.key]: true }));
    }
  };

  return (
    <div className="community-page">
      <TopNav active="home" />
      <main className="community-container home-main">
        <section className="hero-banner" aria-label="시즌 이벤트 프로모션">
          <Image src={HERO_IMAGES[hero]} alt="파우더 슬로프를 라이딩하는 스노보더" fill sizes="(max-width: 1280px) 100vw, 1280px" priority unoptimized />
          <div className="hero-shade" />
          <div className="hero-copy">
            <div className="hero-eyebrow"><span>SEASON EVENT</span><b>24/25 얼리버드 기획전</b></div>
            <h1>첫 파우더를 가르는 설렘,<br /><em>최대 45% 시즌 오픈</em> 스페셜 할인</h1>
            <p>살로몬, 버튼, 오가사카 프리미엄 데크부터 고어텍스 아우터웨어까지. 오직 스노우띵 회원만을 위한 특가 혜택을 놓치지 마세요.</p>
            <div className="hero-actions"><button type="button">기획전 바로가기 <span>›</span></button><button type="button" className="ghost">쿠폰팩 받기</button></div>
          </div>
          <button type="button" className="hero-arrow prev" aria-label="이전 배너" onClick={() => setHero((hero + HERO_IMAGES.length - 1) % HERO_IMAGES.length)}>‹</button>
          <button type="button" className="hero-arrow next" aria-label="다음 배너" onClick={() => setHero((hero + 1) % HERO_IMAGES.length)}>›</button>
          <span className="hero-count"><b>{hero + 1}</b> / {HERO_IMAGES.length}</span>
        </section>

        <div className="mt-4 mb-2">
          <LiveChatSection currentMember={profile} />
        </div>

        <section className="panel resort-panel">
          <div className="panel-heading">
            <h2><span className="status-pulse" />전국 주요 스키장 실시간 슬로프 &amp; 설질 현황 <small>(10분 주기 갱신)</small></h2>
            <div><Link href="/resort">캠 전체보기 ›</Link><button onClick={() => resortRef.current?.scrollBy({ left: -280, behavior: "smooth" })}>‹</button><button onClick={() => resortRef.current?.scrollBy({ left: 280, behavior: "smooth" })}>›</button></div>
          </div>
          <div className="resort-scroller" ref={resortRef}>
            {resorts.map((resort) => (
              <article className="resort-card" key={resort.name}>
                <header><strong>{resort.name}</strong><span className={`operation ${resort.tone}`}>{resort.status}</span><b>{resort.temp}</b></header>
                <dl><div><dt>슬로프 오픈</dt><dd>{resort.open} <em>{resort.detail && `(${resort.detail})`}</em></dd></div><div><dt>신설 / 설질</dt><dd>{resort.snow}</dd></div><div><dt>리프트 혼잡도</dt><dd><span className={`crowd ${resort.tone}`}>{resort.crowd}</span></dd></div></dl>
                <footer><span>{resort.slopes}</span><Link href="/resort">웹캠 보기 ›</Link></footer>
              </article>
            ))}
          </div>
        </section>

        <div className="home-content-grid">
          <section className="home-feed">
            <div className="panel post-board">
              <nav className="board-tabs" aria-label="홈 게시판 선택">
                <div>{HOME_FEED_TABS.map((tab) => <button key={tab.key} type="button" className={activeFeed === tab.key ? "active" : ""} onClick={() => setActiveFeed(tab.key)}>{tab.label}</button>)}</div>
                <Link href={activeFeedConfig.moreHref} className="board-tabs-more">더보기 ›</Link>
              </nav>
              <div className="compact-post-list">
                {feedsLoading ? <div className="home-feed-state"><span className="material-symbols-outlined spin">progress_activity</span><strong>게시글을 불러오는 중입니다.</strong></div>
                  : feedErrors[activeFeed] ? <div className="home-feed-state error"><span className="material-symbols-outlined">cloud_off</span><strong>게시글을 불러오지 못했습니다.</strong><button type="button" onClick={() => void retryFeed(activeFeedConfig)}>다시 시도</button></div>
                  : feeds[activeFeed].length === 0 ? <div className="home-feed-state"><span className="material-symbols-outlined">edit_note</span><strong>등록된 게시글이 없습니다.</strong></div>
                  : feeds[activeFeed].map((post) => (
                    <Link href={`/posts/${post.publicId}`} className="compact-post" key={post.publicId}>
                      <div className="post-summary"><div>{(activeFeed === "all" || activeFeed === "popular") && <b className="category">[{post.categoryCode === "QNA" ? "장비 후기" : post.categoryName}]</b>}<strong>{post.title}</strong>{post.commentCount > 0 && <em>[{post.commentCount}]</em>}{post.hasImage && <small>사진</small>}</div><p><b>{post.writerNickname}</b><span>·</span><time>{new Date(post.createdAt).toLocaleDateString("ko-KR")}</time><span>·</span><span>조회 {post.viewCount.toLocaleString()}</span></p></div>
                      {post.thumbnailImageUrl && <Image src={post.thumbnailImageUrl} alt="" width={64} height={44} unoptimized />}
                    </Link>
                  ))}
              </div>
            </div>

            <section className="panel technique-panel">
              <div className="mini-heading"><h2><i />장비 관리 &amp; 왁싱 핫클립</h2><Link href="/posts?category=QNA">더보기 ›</Link></div>
              <div className="technique-grid">
                <Link href="/posts"><Image src="https://lh3.googleusercontent.com/aida-public/AB6AXuAbp7QUQV_wwAbh0m-xJVicOCADfBGbw42xSkNMgZvcsBs5Unb35kdxi5fefIb-tXBvBMUl6rncUTNmIYxNg-81L6a_EQpSVrRR6cgS18D6sKJG7DetKGDCG14GXTKugzgnj3yCuIK9wfW0wmA5zCB8tsnMmeC0WJn1q0-dMH1EVa344m4jnhuPEXknzEtKdfuVmETtjLCc3EWh3PhfyXKLnQavY-ytAeHb0I4x8BzOJPAWAw9oAD7nKw" alt="스노보드 정비" width={58} height={58} unoptimized/><div><strong>영하 10도 이하 극저온 핫왁싱 블렌딩 및 스크래핑 정석</strong><span>추천 76 · 댓글 28</span></div></Link>
                <Link href="/posts"><Image src="https://lh3.googleusercontent.com/aida-public/AB6AXuCGv-BInEpAeyLpvevZNGu1lq_lcRmTxFguVxcRASFgYmnv_oLNeZCjBRGEgYb22037ZT8d2lXjKhMK1Mlr8d8rcgG_5kCbDXQpG2uJNWgyoVor5Aww4XTiXgu4XJjEYIIq-kNaHNAVhxf6frb8EOxL07nX8Jn5hD6QG6m3o2lPtHHottuKmDARxbHW9jvda6R4sMRQDZHQ8Y0PgkA_5ffFwJowOx8X_ahk0fxsVj61cfGO3PNwZjyTqg" alt="스노보드 엣지" width={58} height={58} unoptimized/><div><strong>사이드 88도 베이스 1도 엣지 홈 다이아몬드 스톤 피니싱 후기</strong><span>추천 49 · 댓글 15</span></div></Link>
              </div>
            </section>
          </section>

          <aside className="home-sidebar">
            <section className="panel snow-report-widget">
              <div className="mini-heading"><h2>❄️ 오늘의 설질 <span>| {koreanToday}</span></h2><Link href="/resort-reports">더보기 ›</Link></div>
              <form className="snow-report-compose" onSubmit={(event) => event.preventDefault()}>
                <select value={snowReportResort} onChange={(event) => setSnowReportResort(event.target.value)} aria-label="리조트 선택">
                  {RESORT_OPTIONS.filter((option) => option.value).map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                </select>
                <input value={snowReportContent} onChange={(event) => setSnowReportContent(event.target.value)} maxLength={100} placeholder="오늘 설질을 한 줄로 알려주세요" aria-label="설질 제보 내용" />
                <button type="submit" disabled={!snowReportContent.trim()}>등록</button>
              </form>
              <div className="snow-report-list">
                {RESORT_REPORT_PREVIEWS.map((report) => {
                  const resort = RESORT_MAP[report.resortCode];
                  return <article key={report.id}><span className={`snow-report-tag ${resort.markerClass}`}>{resort.koreanName}</span><strong>{report.content}</strong><time>{report.time}</time></article>;
                })}
              </div>
            </section>

            <section className="panel carpool-widget" id="carpool">
              <div className="mini-heading"><h2>급구! 카풀 &amp; 동행</h2><Link href="#carpool">+ 등록</Link></div>
              <div>{carpools.map((item) => <article key={item.from}><header><strong>{item.from} <i>→</i> <em>{item.to}</em></strong><span>{item.seat}</span></header><p><b>{item.date}</b><strong>{item.price}</strong></p><footer><span>{item.note}</span><a href="#carpool">신청 ›</a></footer></article>)}</div>
            </section>

            <section className="panel market-widget" id="market">
              <div className="mini-heading"><h2>중고장터</h2><Link href="#market">장터 바로가기 ›</Link></div>
              <div className="market-grid">{gearItems.map((item) => <a href="#market" key={item.title}><div><Image src={item.image} alt={item.title} width={240} height={178} unoptimized/><span>판매중</span></div><strong>{item.title}</strong><b>{item.price}</b><small>{item.note}</small></a>)}</div>
            </section>
          </aside>
        </div>
      </main>
      <Footer />
    </div>
  );
}
