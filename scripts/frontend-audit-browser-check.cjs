// 2026-11-20 프론트 점검에서 고친 화면 동작을 실제 브라우저로 확인한다. 목 API를 스스로 띄운다.
// 준비: cd frontend && NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8130 npx next dev -p 3130
// 실행: LOCAL_BROWSER_ORIGIN=http://localhost:3130 node scripts/frontend-audit-browser-check.cjs
const assert = require('node:assert/strict');
const http = require('node:http');
const { withLocalBrowser } = require('./lib/local-browser.cjs');

const MOCK_PORT = Number(process.env.MOCK_API_PORT || 8130);
const state = { reportsFail: true, deleteCalls: 0, deleteDelayMs: 600, reactionCalls: 0 };
const reports = [
  { reportId: 11, resortId: 1, resortName: '휘닉스파크', resortCode: 'PHOENIX', authorNickname: '보드타는곰', content: '오전 설질 좋아요', createdAt: '2026-11-20T09:10:00+09:00', canDelete: true },
  { reportId: 12, resortId: 2, resortName: '비발디파크', resortCode: 'VIVALDI', authorNickname: '스키러', content: '정설 잘 되어 있음', createdAt: '2026-11-20T08:40:00+09:00', canDelete: false },
];

const POST_ID = '00000000-0000-4000-8000-000000000001';
const post = {
  publicId: POST_ID, categoryName: '익명게시판', categoryCode: 'ANONYMOUS', title: '점검용 익명글', content: '본문입니다.', status: 'NORMAL',
  isAnonymous: true, isAuthor: false, requiresPassword: true, viewCount: 1, commentCount: 0, likeCount: 0, dislikeCount: 0,
  activeReactionTypes: [], scrapped: false, writer: { publicId: null, nickname: '익명', profileImageUrl: null }, images: [], createdAt: '2026-11-20T09:00:00+09:00',
};

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', req.headers.origin || '*');
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-XSRF-TOKEN, Idempotency-Key');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }
  const json = (status, body) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)); };
  const { pathname } = new URL(req.url, 'http://mock');
  if (pathname === '/api/v1/csrf') return json(200, { token: 'mock' });
  if (pathname === '/api/v1/members/me') return json(401, { code: 'AUTH_001' });
  if (pathname === '/api/v1/master/resorts') return json(200, [{ id: 1, name: '휘닉스파크' }, { id: 2, name: '비발디파크' }]);
  if (pathname === '/api/v1/resort-reports/today') {
    // 앞단 장애처럼 HTML로 답한다.
    if (state.reportsFail) { res.writeHead(502, { 'Content-Type': 'text/html' }); return res.end('<html>502 Bad Gateway</html>'); }
    return json(200, { content: reports, pageInfo: { page: 1, totalPages: 1, totalElements: reports.length, hasNext: false, pageSize: 20 } });
  }
  if (pathname === `/api/v1/posts/${POST_ID}/reaction`) {
    state.reactionCalls += 1;
    return setTimeout(() => json(200, { message: '추천했습니다.', likeCount: 1, dislikeCount: 0, active: true }), 500);
  }
  if (pathname === `/api/v1/posts/${POST_ID}/comments` && req.method === 'POST') {
    // 앞단이 본문 크기 제한으로 HTML 413을 돌려주는 경우
    res.writeHead(413, { 'Content-Type': 'text/html' }); return res.end('<html>413 Request Entity Too Large</html>');
  }
  if (pathname === `/api/v1/posts/${POST_ID}/comments`) return json(200, { publicId: POST_ID, totalCommentCount: 0, comments: [], nextCursor: null, hasNext: false });
  if (pathname === `/api/v1/posts/${POST_ID}`) {
    // 삭제는 앞단 장애처럼 HTML로 답한다. 화면에 영어 파싱 문구가 나오면 안 된다.
    if (req.method === 'DELETE') { res.writeHead(502, { 'Content-Type': 'text/html' }); return res.end('<html>502 Bad Gateway</html>'); }
    return json(200, post);
  }
  if (pathname.startsWith('/api/v1/resort-reports/') && req.method === 'DELETE') {
    state.deleteCalls += 1;
    return setTimeout(() => json(200, {}), state.deleteDelayMs);
  }
  return json(404, { code: 'NOT_FOUND' });
});

server.listen(MOCK_PORT, '127.0.0.1', () => {
  withLocalBrowser('frontend-audit', async ({ navigate, evaluate, waitFor, viewport, confirmNext, pause }) => {
    await viewport(390, 844);

    // 1. 목록 요청이 실패하면 "제보 없음"이 아니라 실패 안내와 다시 시도 버튼이 보인다.
    await navigate('/resort-reports');
    await waitFor('!!document.querySelector(".resort-report-board [role=alert]")');
    const failedText = await evaluate('document.querySelector(".resort-report-board").innerText');
    assert.ok(failedText.includes('불러오지 못했습니다'), failedText);
    assert.ok(!failedText.includes('아직 없습니다'), 'Failure must not look like an empty list');
    assert.ok(!failedText.includes('0건'), 'Failure must not show a zero count');

    // 2. 다시 시도하면 목록이 보인다.
    state.reportsFail = false;
    await evaluate('[...document.querySelectorAll(".resort-report-board button")].find(b => b.textContent.includes("다시 시도")).click()');
    await waitFor('document.querySelectorAll(".resort-report-board .snow-report-row").length === 2');
    assert.ok((await evaluate('document.querySelector(".resort-report-board header").innerText')).includes('2건'));

    // 3. 삭제를 빠르게 두 번 눌러도 요청은 한 번만 나간다.
    confirmNext(true);
    await evaluate('document.querySelector(".snow-report-delete").click()');
    await waitFor('document.querySelector(".snow-report-delete")?.disabled === true');
    await evaluate('document.querySelector(".snow-report-delete").click()');
    await pause(state.deleteDelayMs + 800);
    assert.equal(state.deleteCalls, 1, 'Double click should send one DELETE');

    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true);

    // 4. 삭제 확인 창: 열리면 초점이 창 안으로 가고, 서버가 HTML로 답해도 한국어 안내가 보이고, Esc로 닫으면 초점이 돌아온다.
    await navigate(`/posts/${POST_ID}`);
    const deleteButton = '[...document.querySelectorAll(".post-detail-owner-actions button")].find(b => b.textContent.trim() === "삭제")';
    await waitFor(`!!${deleteButton}`);
    await evaluate(`(() => { const b = ${deleteButton}; b.focus(); b.click(); })()`);
    await waitFor('!!document.querySelector(".delete-modal")');
    await waitFor('document.activeElement?.matches(".delete-modal input[type=password]")');
    await evaluate(`(() => { const input = document.activeElement; Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, '1234'); input.dispatchEvent(new Event('input', { bubbles: true })); })()`);
    await evaluate('document.querySelector(".delete-modal-confirm").click()');
    await waitFor('!!document.querySelector(".delete-modal-error")');
    const modalError = await evaluate('document.querySelector(".delete-modal-error").textContent');
    assert.ok(!/Unexpected|JSON|token/i.test(modalError), modalError);
    assert.ok(/[가-힣]/.test(modalError), modalError);
    await evaluate(`document.activeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))`);
    await waitFor('!document.querySelector(".delete-modal")');
    assert.equal(await evaluate(`document.activeElement === ${deleteButton}`), true, 'Focus should return to the delete button');

    // 5. 추천을 빠르게 두 번 눌러도 요청은 한 번만 나간다.
    await evaluate('(() => { const b = document.querySelector(".post-vote button"); b.click(); b.click(); })()');
    await waitFor('document.querySelector(".post-vote button strong")?.textContent === "1"');
    await pause(300);
    assert.equal(state.reactionCalls, 1, 'Double click on like should send one request');

    // 7. 게시판 탭은 색만이 아니라 aria-pressed로도 선택 상태를 알린다.
    await navigate('/posts?category=FREE');
    await waitFor('!!document.querySelector(".board-category-tabs button[aria-pressed=true]")');
    assert.equal(await evaluate('document.querySelector(".board-category-tabs button[aria-pressed=true]").textContent.trim()'), '자유게시판');
    assert.equal(await evaluate('document.querySelectorAll(".board-category-tabs button[aria-pressed=true]").length'), 1);
    await navigate('/');
    await waitFor('!!document.querySelector(".board-tabs button[aria-pressed=true]")');
    assert.equal(await evaluate('document.querySelector(".board-tabs button[aria-pressed=true]").textContent.trim()'), '전체글');
    await navigate(`/posts/${POST_ID}`);
    await waitFor('!!document.querySelector(".post-comment-compose textarea")');

    // 6. 댓글 등록이 HTML 413으로 거절되면 연결 오류가 아니라 크기 안내를 보여 준다.
    await evaluate('window.__alerts = []; window.alert = (message) => window.__alerts.push(String(message));');
    const setValue = (selector, value) => `(() => { const el = document.querySelector(${JSON.stringify(selector)}); const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype; Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, ${JSON.stringify(value)}); el.dispatchEvent(new Event('input', { bubbles: true })); })()`;
    await evaluate(setValue('.post-comment-compose textarea', '점검용 댓글'));
    await evaluate(setValue('.post-comment-password input', '1234'));
    await evaluate('document.querySelector(".post-comment-submit-button").click()');
    await waitFor('window.__alerts.length > 0');
    const alertText = await evaluate('window.__alerts[0]');
    assert.ok(alertText.includes('너무 큽니다'), alertText);
  })
    .then(() => { console.log('frontend audit browser check passed'); })
    .catch((error) => { console.error(error); process.exitCode = 1; })
    .finally(() => server.close());
});
