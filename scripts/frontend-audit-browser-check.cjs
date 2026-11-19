// 2026-11-20 프론트 점검에서 고친 화면 동작을 실제 브라우저로 확인한다. 목 API를 스스로 띄운다.
// 준비: cd frontend && NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8130 npx next dev -p 3130
// 실행: LOCAL_BROWSER_ORIGIN=http://localhost:3130 node scripts/frontend-audit-browser-check.cjs
const assert = require('node:assert/strict');
const http = require('node:http');
const { withLocalBrowser } = require('./lib/local-browser.cjs');

const MOCK_PORT = Number(process.env.MOCK_API_PORT || 8130);
const state = { reportsFail: true, deleteCalls: 0, deleteDelayMs: 600 };
const reports = [
  { reportId: 11, resortId: 1, resortName: '휘닉스파크', resortCode: 'PHOENIX', authorNickname: '보드타는곰', content: '오전 설질 좋아요', createdAt: '2026-11-20T09:10:00+09:00', canDelete: true },
  { reportId: 12, resortId: 2, resortName: '비발디파크', resortCode: 'VIVALDI', authorNickname: '스키러', content: '정설 잘 되어 있음', createdAt: '2026-11-20T08:40:00+09:00', canDelete: false },
];

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
  })
    .then(() => { console.log('frontend audit browser check passed'); })
    .catch((error) => { console.error(error); process.exitCode = 1; })
    .finally(() => server.close());
});
