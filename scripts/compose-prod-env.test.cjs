// 운영 컨테이너에는 compose.prod.yml의 environment 목록에 적힌 변수만 들어간다.
// /etc/snowthing/prod.env에 값을 넣어도 목록에 없으면 백엔드는 기본값으로 돈다.
// application.yml에 새 환경 변수를 추가하면 compose.prod.yml에 넣거나, 기본값으로 충분한 이유와 함께 아래 목록에 넣는다.
// 실행: node --test scripts/compose-prod-env.test.cjs
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// 운영에서 바꿀 일이 없어 코드 기본값을 그대로 쓰는 변수. 운영에서 바꿔야 하면 여기서 빼고 compose.prod.yml에 넣는다.
const CODE_DEFAULT_ONLY = new Set([
  // 외부 API 시간 제한
  'SNOWTHING_CARPOOL_CONNECT_TIMEOUT_MILLIS',
  'SNOWTHING_CARPOOL_READ_TIMEOUT_MILLIS',
  'SNOWTHING_OAUTH2_CONNECT_TIMEOUT_MILLIS',
  'SNOWTHING_OAUTH2_READ_TIMEOUT_MILLIS',
  'SNOWTHING_WEATHER_CONNECT_TIMEOUT_MILLIS',
  'SNOWTHING_WEATHER_READ_TIMEOUT_MILLIS',
  // 슬로프 현황 수집 주기
  'SNOWTHING_SLOPE_STATUS_CRON',
  // AI 회원 시즌 기준일. 기본값은 application.yml에서 시즌마다 바꾼다.
  'SNOWTHING_AI_MEMBERS_FIRST_OPENING_DATE',
  // 쪽지 메일 SES 주소. 로컬 시험용이고 운영은 SDK 기본 주소를 쓴다.
  'SNOWTHING_DM_MAIL_SES_ENDPOINT',
  // AI 답변 초안 공개와 검색 색인, 의미 검색(IKA-5). 운영에서는 꺼 두거나 코드 기본값으로 돈다.
  'SNOWTHING_ANSWER_DRAFT_PUBLISH_ENABLED',
  'SNOWTHING_SEARCH_BOOTSTRAP_ON_EMPTY',
  'SNOWTHING_SEARCH_EMBEDDING_API_KEY',
  'SNOWTHING_SEARCH_EMBEDDING_BASE_URL',
  'SNOWTHING_SEARCH_EMBEDDING_DIMENSION',
  'SNOWTHING_SEARCH_EMBEDDING_MODEL',
  'SNOWTHING_SEARCH_INDEXER_ENABLED',
  'SNOWTHING_SEARCH_INDEXER_POLL_INTERVAL_MS',
  'SNOWTHING_SEARCH_RECONCILE_ENABLED',
  'SNOWTHING_SEARCH_SEMANTIC_CANDIDATE_K',
  'SNOWTHING_SEARCH_SEMANTIC_ENABLED',
  'SNOWTHING_SEARCH_SEMANTIC_MAX_VECTOR_SCAN',
  'SNOWTHING_SEARCH_SEMANTIC_PROVIDER',
  'SNOWTHING_SEARCH_SEMANTIC_RRF_K',
]);

function applicationVariables() {
  const directory = 'backend/src/main/resources';
  const names = new Set();
  for (const file of fs.readdirSync(path.join(root, directory))) {
    if (!/^application.*\.ya?ml$/.test(file)) continue;
    for (const match of read(`${directory}/${file}`).matchAll(/\$\{([A-Z][A-Z0-9_]+)/g)) {
      names.add(match[1]);
    }
  }
  return names;
}

function backendComposeVariables() {
  const compose = read('compose.prod.yml');
  const backend = compose.slice(compose.indexOf('\n  backend:'), compose.indexOf('\n  frontend:'));
  assert.ok(backend.length > 0, 'compose.prod.yml에서 backend 서비스를 찾지 못했다');
  return new Set([...backend.matchAll(/^\s+- ([A-Z][A-Z0-9_]+)=/gm)].map((match) => match[1]));
}

test('application.yml의 환경 변수는 compose.prod.yml로 전달되거나 코드 기본값 목록에 있다', () => {
  const passed = backendComposeVariables();
  const missing = [...applicationVariables()].filter((name) => !passed.has(name) && !CODE_DEFAULT_ONLY.has(name));
  assert.deepEqual(missing, [], `compose.prod.yml backend environment에 없는 변수: ${missing.join(', ')}`);
});

test('코드 기본값 목록에는 실제로 쓰는 변수만 남긴다', () => {
  const used = applicationVariables();
  const passed = backendComposeVariables();
  const stale = [...CODE_DEFAULT_ONLY].filter((name) => !used.has(name) || passed.has(name));
  assert.deepEqual(stale, [], `목록에서 지울 변수: ${stale.join(', ')}`);
});

test('운영에서 켜는 슬로프 현황 리조트 목록은 컨테이너로 전달된다', () => {
  assert.ok(backendComposeVariables().has('SNOWTHING_SLOPE_STATUS_ENABLED_RESORTS'));
});

test('로컬 개발 DB 포트는 이 PC(127.0.0.1)에서만 연다', () => {
  const ports = [...read('docker-compose.yml').matchAll(/^\s+- "([^"]*:3306)"$/gm)].map((match) => match[1]);
  assert.deepEqual(ports, ['127.0.0.1:3306:3306']);
});
