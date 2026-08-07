const boardTabs = ["전체", "자유", "익명", "카풀", "장비 VS", "맛집"];

const posts = [
  {
    board: "자유",
    title: "오늘 휘팍 야간 타는 사람들 설질 어때?",
    body: "중단부는 단단하고 초보 슬로프는 8시 이후부터 사람 빠진다는 댓글이 많습니다.",
    comments: 24,
    views: 318,
    time: "8분 전"
  },
  {
    board: "익명",
    title: "초보인데 혼자 가면 너무 눈치 보일까",
    body: "혼자 오는 사람 많다는 반응이 우세하고, 리프트 탑승보다 슬로프 합류 타이밍을 조심하라는 조언이 많습니다.",
    comments: 41,
    views: 522,
    time: "19분 전"
  },
  {
    board: "카풀",
    title: "토요일 새벽 강남 출발 휘팍 2자리",
    body: "편도 기준 1인 23,000원 예상. 장비 적재 가능 여부를 먼저 확인해야 합니다.",
    comments: 11,
    views: 176,
    time: "34분 전"
  },
  {
    board: "장비 VS",
    title: "입문자가 첫 데크로 올라운드 사는 거 오버스펙?",
    body: "현재 올라운드 57%, 입문 데크 43%. 투표 댓글은 익명으로 진행 중입니다.",
    comments: 36,
    views: 447,
    time: "52분 전"
  }
];

const matches = [
  {
    name: "민준",
    resort: "휘닉스 평창",
    time: "오늘 19:00",
    style: "카빙 연습",
    level: "중급",
    score: "92%"
  },
  {
    name: "서연",
    resort: "용평",
    time: "내일 오전",
    style: "초보 환영",
    level: "초급",
    score: "87%"
  }
];

const liftReports = [
  { lift: "펭귄", state: "보통", updated: "4분 전" },
  { lift: "호크", state: "혼잡", updated: "7분 전" },
  { lift: "챔피언", state: "판단불가", updated: "12분 전" }
];

export default function Home() {
  return (
    <main className="app">
      <header className="topbar">
        <div className="brand">
          <span className="mark">S</span>
          <div>
            <strong>Snowthing</strong>
            <small>스노보더 커뮤니티</small>
          </div>
        </div>

        <nav className="nav" aria-label="Primary navigation">
          <a href="#boards">게시판</a>
          <a href="#matching">같이타요</a>
          <a href="#carpool">카풀</a>
          <a href="#reports">리프트 제보</a>
        </nav>

        <button className="writeButton">글쓰기</button>
      </header>

      <section className="hero">
        <div>
          <p className="eyebrow">Snowboard community</p>
          <h1>❄️눈팅❄️</h1>
          <p className="lead">
            자유/익명 게시판 흐름을 AI가 요약하고, 카풀·장비 VS·맛집·리프트 제보·같이타요 매칭을 하나의 커뮤니티 안에서 연결합니다.
          </p>
        </div>
        <aside className="summaryCard" aria-label="AI community summary">
          <div className="cardHead">
            <span>AI 요약</span>
            <b>최근 3시간</b>
          </div>
          <p>
            오늘은 휘팍 야간 설질, 초보 혼보딩, 토요일 카풀 글이 가장 활발합니다. 익명게시판은 초보 동선 질문이 많고,
            장비 VS는 입문 데크 논쟁으로 댓글이 빠르게 늘고 있습니다.
          </p>
        </aside>
      </section>

      <section className="layout">
        <section className="boardPanel" id="boards">
          <div className="sectionTitle">
            <div>
              <p className="eyebrow">Boards</p>
              <h2>커뮤니티 게시판</h2>
            </div>
            <div className="tabs" aria-label="Board filters">
              {boardTabs.map((tab) => (
                <button className={tab === "전체" ? "active" : ""} key={tab}>
                  {tab}
                </button>
              ))}
            </div>
          </div>

          <div className="postList">
            {posts.map((post) => (
              <article className="post" key={post.title}>
                <div className="postTop">
                  <span className="boardName">{post.board}</span>
                  <time>{post.time}</time>
                </div>
                <h3>{post.title}</h3>
                <p>{post.body}</p>
                <footer>
                  <span>댓글 {post.comments}</span>
                  <span>조회 {post.views}</span>
                  <button>AI 요약</button>
                </footer>
              </article>
            ))}
          </div>
        </section>

        <aside className="side">
          <section className="panel matching" id="matching">
            <div className="sectionTitle small">
              <div>
                <p className="eyebrow">Matching</p>
                <h2>같이타요 매칭</h2>
              </div>
              <span className="status on">ON</span>
            </div>

            <div className="matchCondition">
              <span>휘닉스 평창</span>
              <span>오늘 야간</span>
              <span>중급</span>
              <span>카빙 연습</span>
            </div>

            <div className="matchList">
              {matches.map((match) => (
                <article className="matchItem" key={match.name}>
                  <div className="avatar">{match.name[0]}</div>
                  <div>
                    <strong>{match.name}</strong>
                    <p>{match.resort} · {match.time}</p>
                    <small>{match.level} · {match.style}</small>
                  </div>
                  <b>{match.score}</b>
                </article>
              ))}
            </div>
          </section>

          <section className="panel" id="carpool">
            <div className="sectionTitle small">
              <div>
                <p className="eyebrow">Carpool</p>
                <h2>카풀 모집</h2>
              </div>
              <span className="status">계산기</span>
            </div>
            <div className="fare">
              <div>
                <span>강남 → 휘팍</span>
                <strong>1인 23,000원</strong>
              </div>
              <button>계산</button>
            </div>
          </section>

          <section className="panel" id="reports">
            <div className="sectionTitle small">
              <div>
                <p className="eyebrow">Lift reports</p>
                <h2>리프트 줄 제보</h2>
              </div>
              <span className="status">AI</span>
            </div>
            <div className="reportList">
              {liftReports.map((report) => (
                <div className="report" key={report.lift}>
                  <strong>{report.lift}</strong>
                  <span>{report.state}</span>
                  <small>{report.updated}</small>
                </div>
              ))}
            </div>
          </section>
        </aside>
      </section>
    </main>
  );
}
