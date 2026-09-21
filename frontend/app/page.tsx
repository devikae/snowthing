"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Footer, TopNav } from "./components/SiteChrome";
import LiveChatSection, { MemberProfile } from "./components/LiveChatSection";
import { API_ENDPOINTS } from "./lib/api";

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

const posts = [
  { vote: 88, category: "휘팍", title: "오늘 챔피언 실시간 설질 뜸 (감자 없음, 찹쌀떡 파우더)", comments: 42, author: "파우더헌터_민우", time: "18분 전", views: "1,840", hot: true, image: HERO_IMAGES[1] },
  { vote: 54, category: "장비/리뷰", title: "2425 살로몬 하이랜드 바인딩 세팅값 +21/-6 후기", comments: 31, author: "카빙마스터99", time: "42분 전", views: "1,209", image: HERO_IMAGES[0] },
  { vote: 37, category: "팁/강좌", title: "야간 하이원 아폴로 렌즈 클리어 vs 옐로우 실착 비교", comments: 19, author: "설원산책러", time: "1시간 전", views: "892", image: HERO_IMAGES[1] },
  { vote: 112, category: "안전주의", title: "웰팍 C3 슬로프 모글밭 됐네요 안전 라이딩 하세요", comments: 65, author: "둔내패트롤조언자", time: "2시간 전", views: "3,410", hot: true },
  { vote: 18, category: "묻고답하기", title: "오가사카 FC-S 160 vs 에이펙스 티탄날 고민입니다", comments: 24, author: "해머초보7년차", time: "3시간 전", views: "740" },
  { vote: 22, category: "자유게시판", title: "초보자 엉덩이 보호대 플렉시 지폼 vs 파워텍터 추천", comments: 16, author: "안전라이더Kim", time: "3시간 전", views: "980" },
  { vote: 41, category: "자유게시판", title: "주말 강원도권 고속도로 제설 상황 및 미시령 터널 소통 원활합니다", comments: 13, author: "용평지박령", time: "4시간 전", views: "1,120" },
];

const anonymousPosts = [
  { author: "익명보더", vote: 45, title: "솔직히 주말에 셔틀버스 안에서 냄새나는 장비 방치 좀 하지 맙시다", body: "젖은 부츠랑 땀 찬 장갑 통로 바닥에 굴러다니는데 매너 좀 지키세요." },
  { author: "시즌방총무", vote: 92, title: "시즌방 3년 차가 털어놓는 시즌방 빌런 유형 TOP 5", body: "1위: 장보기 비용 정산 담당 가족 셀프 리포트부터 시작합니다." },
  { author: "익명라이더", vote: 28, title: "곤돌라 같이 타고 올라가다가 데크 그래픽으로 말 튼 번호 땄다", body: "서로 같은 라인 타는 거 보고 정상 휴게소에서 커피 한잔하자고 함." },
];

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
  const resortRef = useRef<HTMLDivElement>(null);

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

  return (
    <div className="community-page">
      <TopNav active="home" />
      <main className="community-container home-main">
        <section className="hero-banner" aria-label="시즌 이벤트 프로모션">
          <img src={HERO_IMAGES[hero]} alt="파우더 슬로프를 라이딩하는 스노보더" />
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
              <div className="board-tabs"><div><button className="active">전체글</button><button>실시간 베스트</button><button>자유게시판</button><button>장비·왁싱</button><button>슬로프 갤러리</button><button>묻고답하기</button></div><select aria-label="게시글 정렬"><option>최신순</option><option>추천순</option><option>댓글순</option></select></div>
              <div className="compact-post-list">
                {posts.map((post) => (
                  <Link href="/posts" className={post.category === "안전주의" ? "compact-post warning" : "compact-post"} key={post.title}>
                    <span className={post.hot ? "vote hot" : "vote"}>▲ {post.vote}</span>
                    <div className="post-summary"><div><b className={post.category === "안전주의" ? "category danger" : "category"}>[{post.category}]</b><strong>{post.title}</strong><em>[{post.comments}]</em>{post.image && <small>사진</small>}</div><p><b>{post.author}</b><span>·</span><span>{post.time}</span><span>·</span><span>조회 {post.views}</span></p></div>
                    {post.image && <img src={post.image} alt="" />}
                  </Link>
                ))}
              </div>
              <div className="board-footer"><div className="pagination"><button className="current">1</button><button>2</button><button>3</button><button>4</button><button>5</button><span>…</span><button>다음 ›</button></div><form onSubmit={(event) => event.preventDefault()}><input placeholder="게시판 내 검색"/><button>검색</button></form></div>
            </div>

            <section className="panel technique-panel">
              <div className="mini-heading"><h2><i />장비 관리 &amp; 왁싱 핫클립</h2><Link href="/posts?category=QNA">더보기 ›</Link></div>
              <div className="technique-grid">
                <Link href="/posts"><img src="https://lh3.googleusercontent.com/aida-public/AB6AXuAbp7QUQV_wwAbh0m-xJVicOCADfBGbw42xSkNMgZvcsBs5Unb35kdxi5fefIb-tXBvBMUl6rncUTNmIYxNg-81L6a_EQpSVrRR6cgS18D6sKJG7DetKGDCG14GXTKugzgnj3yCuIK9wfW0wmA5zCB8tsnMmeC0WJn1q0-dMH1EVa344m4jnhuPEXknzEtKdfuVmETtjLCc3EWh3PhfyXKLnQavY-ytAeHb0I4x8BzOJPAWAw9oAD7nKw" alt="스노보드 정비"/><div><strong>영하 10도 이하 극저온 핫왁싱 블렌딩 및 스크래핑 정석</strong><span>추천 76 · 댓글 28</span></div></Link>
                <Link href="/posts"><img src="https://lh3.googleusercontent.com/aida-public/AB6AXuCGv-BInEpAeyLpvevZNGu1lq_lcRmTxFguVxcRASFgYmnv_oLNeZCjBRGEgYb22037ZT8d2lXjKhMK1Mlr8d8rcgG_5kCbDXQpG2uJNWgyoVor5Aww4XTiXgu4XJjEYIIq-kNaHNAVhxf6frb8EOxL07nX8Jn5hD6QG6m3o2lPtHHottuKmDARxbHW9jvda6R4sMRQDZHQ8Y0PgkA_5ffFwJowOx8X_ahk0fxsVj61cfGO3PNwZjyTqg" alt="스노보드 엣지"/><div><strong>사이드 88도 베이스 1도 엣지 홈 다이아몬드 스톤 피니싱 후기</strong><span>추천 49 · 댓글 15</span></div></Link>
              </div>
            </section>
          </section>

          <aside className="home-sidebar">
            <section className="panel anonymous-widget">
              <div className="mini-heading"><h2><span>HOT</span>익명게시판</h2><Link href="/posts?category=ANONYMOUS">전체보기 ›</Link></div>
              <div>{anonymousPosts.map((post) => <Link href="/posts?category=ANONYMOUS" key={post.title}><p><b>{post.author}</b><em>추천 {post.vote}</em></p><strong>{post.title}</strong><span>{post.body}</span></Link>)}</div>
              <form onSubmit={(event) => event.preventDefault()}><input placeholder="익명으로 글 남기기..."/><button>등록</button></form>
            </section>

            <section className="panel carpool-widget" id="carpool">
              <div className="mini-heading"><h2><span className="blue">실시간</span>급구! 카풀 &amp; 동행</h2><Link href="#carpool">+ 등록</Link></div>
              <div>{carpools.map((item) => <article key={item.from}><header><strong>{item.from} <i>→</i> <em>{item.to}</em></strong><span>{item.seat}</span></header><p><b>{item.date}</b><strong>{item.price}</strong></p><footer><span>{item.note}</span><a href="#carpool">신청 ›</a></footer></article>)}</div>
            </section>

            <section className="panel market-widget" id="market">
              <div className="mini-heading"><h2>중고장터 실시간 매물</h2><Link href="#market">장터 바로가기 ›</Link></div>
              <div className="market-grid">{gearItems.map((item) => <a href="#market" key={item.title}><div><img src={item.image} alt={item.title}/><span>판매중</span></div><strong>{item.title}</strong><b>{item.price}</b><small>{item.note}</small></a>)}</div>
            </section>
          </aside>
        </div>
      </main>
      <Footer />
    </div>
  );
}
