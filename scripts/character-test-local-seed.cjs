#!/usr/bin/env node
// 관계 지도를 여러 명이 찬 상태로 보려는 로컬 전용 시드. 로컬 Docker MySQL 컨테이너에만 쓴다. 운영 DB에 쓰면 안 된다.
//
// 사용:
//   node scripts/character-test-local-seed.cjs --database snowthing_ika27_local --count 40
//   node scripts/character-test-local-seed.cjs --database snowthing_ika27_local --clean
// 옵션: --container 컨테이너 이름(기본 snowthing-mysql), --base-url 복귀 링크 주소(기본 http://localhost:3000)
//
// 다음 경우 실행을 거부한다.
//   원격 Docker 호스트, 로컬이 아닌 Docker 컨텍스트, mysql 이미지가 아닌 컨테이너,
//   이름이 snowthing 또는 snowthing_로 시작하지 않거나 prod가 들어간 스키마, 운영 프로필 환경 변수.
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');

const OWNER_NICKNAME = '김민준';
const NICKNAMES = '민수 지은 서연 도윤 하늘 수빈 재현 유나 현우 지호 소라 정민 예린 동현 미나 준서 은지 건우 나래 승호 다솜 영훈 혜진 상우 김태호 이수민 박지훈 최유진 정하늘 강민호 윤서아 장도현 임채원 신유리 권혁준 송지아 안재민 홍예지 전민재 고은비 문성훈 양지수 배수연 노태윤 서지안 심규민 백하람 남도영 차서윤 류현서'.split(' ');
const GROUP_NAME = `${OWNER_NICKNAME}의 모임`;
const MAX_PARTICIPANTS = 50;

function fail(message) {
  console.error(`거부: ${message}`);
  process.exit(1);
}

function option(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

function docker(...args) {
  return execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}

function assertLocalTarget(container, database) {
  if (!database) fail('--database를 직접 적어 주세요. 기본값을 두지 않습니다.');
  if (!/^snowthing(_[a-z0-9_]+)?$/.test(database) || database.includes('prod')) fail(`로컬 스키마 이름이 아닙니다: ${database}`);
  if ((process.env.SPRING_PROFILES_ACTIVE || '').includes('prod')) fail('운영 프로필 환경에서는 실행하지 않습니다.');
  const dockerHost = process.env.DOCKER_HOST || '';
  if (dockerHost && !/^(unix|npipe):/.test(dockerHost)) fail('원격 Docker 호스트에는 쓰지 않습니다.');
  const endpoint = docker('context', 'inspect', '--format', '{{.Endpoints.docker.Host}}');
  if (!/^(unix|npipe):/.test(endpoint)) fail(`로컬 Docker 컨텍스트가 아닙니다: ${endpoint}`);
  const image = docker('inspect', '--format', '{{.Config.Image}}', container);
  if (!/^mysql(:|$)/.test(image)) fail(`mysql 컨테이너가 아닙니다: ${image}`);
}

function runSql(container, database, sql) {
  return execFileSync('docker', ['exec', '-i', container, 'sh', '-c', `mysql --default-character-set=utf8mb4 -N -uroot -p"$MYSQL_ROOT_PASSWORD" ${database} 2>/dev/null`], { input: sql, encoding: 'utf8' });
}

// 15자리 답변의 앞 12자리로 내부 유형을 계산한다(서버 CharacterAnswers와 같은 규칙). 시드 도구 안에서만 쓴다.
function typeOf(answers) {
  const axes = ['EI', 'SN', 'TF', 'JP'];
  return axes.map((axis, index) => {
    const chunk = answers.slice(index * 3, index * 3 + 3);
    return axis[[...chunk].filter(char => char === 'A').length >= 2 ? 0 : 1];
  }).join('');
}

function randomAnswers() {
  const type = Array.from({ length: 12 }, () => (crypto.randomInt(2) === 0 ? 'A' : 'B')).join('');
  const habits = Array.from({ length: 3 }, () => 'ABC'[crypto.randomInt(3)]).join('');
  return type + habits;
}

function main() {
  const container = option('container', 'snowthing-mysql');
  const database = option('database');
  assertLocalTarget(container, database);

  if (process.argv.includes('--clean')) {
    runSql(container, database, `DELETE p FROM character_participant p JOIN character_group g ON p.character_group_id = g.character_group_id WHERE g.name = '${GROUP_NAME}'; DELETE FROM character_group WHERE name = '${GROUP_NAME}';`);
    console.log('시드 모임을 지웠습니다.');
    return;
  }

  const count = Number(option('count', '40'));
  if (!Number.isInteger(count) || count < 2 || count > MAX_PARTICIPANTS) fail(`--count는 2 이상 ${MAX_PARTICIPANTS} 이하입니다.`);
  const baseUrl = option('base-url', 'http://localhost:3000');
  const groupId = crypto.randomUUID();
  const people = Array.from({ length: count }, (_, index) => {
    const token = crypto.randomBytes(32).toString('base64url');
    const answers = randomAnswers();
    return {
      nickname: index === 0 ? OWNER_NICKNAME : NICKNAMES[index - 1],
      sport: crypto.randomInt(2) === 0 ? 'SKI' : 'BOARD',
      answers,
      type: typeOf(answers),
      token,
      hash: crypto.createHash('sha256').update(token).digest('hex'),
      publicId: crypto.randomUUID(),
    };
  });
  const now = 'DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 HOUR)';
  const rows = people.map(person => `('${person.publicId}', @group_id, '${person.nickname}', '${person.sport}', '${person.answers}', '${person.type}', '${person.hash}', ${now})`).join(',\n');
  runSql(container, database, `START TRANSACTION;
INSERT INTO character_group (public_id, name, owner_participant_id, created_at, updated_at) VALUES ('${groupId}', '${GROUP_NAME}', NULL, ${now}, ${now});
SET @group_id = LAST_INSERT_ID();
INSERT INTO character_participant (public_id, character_group_id, nickname, sport, answers, type_code, access_token_hash, created_at) VALUES
${rows};
UPDATE character_group SET owner_participant_id = (SELECT character_participant_id FROM character_participant WHERE public_id = '${people[0].publicId}') WHERE character_group_id = @group_id;
COMMIT;
`);

  const link = person => `${baseUrl}/character-test/g/${groupId}#t=${person.token}`;
  console.log(`${database}에 ${count}명이 있는 모임을 만들었습니다. 아래 링크는 로컬 복귀 링크입니다.`);
  console.log(`새 사람으로 참여: ${baseUrl}/character-test/g/${groupId}`);
  console.log(`만든 사람(${people[0].nickname}): ${link(people[0])}`);
  for (const person of people.slice(1, 4)) console.log(`${person.nickname}: ${link(person)}`);
}

main();
