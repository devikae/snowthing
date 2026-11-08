// 캐릭터 테스트의 화면 코드, 공개 이미지, 빌드 산출물에 안쪽 유형 이름이 나가지 않는지 확인한다.
// 실행: node --test scripts/character-test-no-mbti.test.cjs (npm run build 뒤에 돌리면 빌드 산출물까지 본다)
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const frontend = path.join(__dirname, '../frontend');
const word = ['M', 'B', 'T', 'I'].join('');
const forbidden = new RegExp(`${word}|\\b[EI][SN][TF][JP]\\b`, 'i');
const textFile = /\.(tsx?|jsx?|mjs|cjs|css|json|html|txt|rsc|map)$/;

function walk(directory, found = []) {
  if (!fs.existsSync(directory)) return found;
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(full, found);
    else found.push(full);
  }
  return found;
}

function hits(files) {
  const result = [];
  for (const file of files) {
    if (!textFile.test(file)) continue;
    const match = fs.readFileSync(file, 'utf8').match(forbidden);
    if (match) result.push(`${path.relative(frontend, file)}: ${match[0]}`);
  }
  return result;
}

test('프론트 소스 전체에 유형 이름이 없다', () => {
  assert.deepEqual(hits(walk(path.join(frontend, 'app'))), []);
});

test('공개 그림 파일 이름은 직업 코드 8개, 장비 9개, 미리보기 2개뿐이고 메타데이터 글자가 없다', () => {
  const directory = path.join(frontend, 'public/character-test');
  const names = fs.readdirSync(directory).sort();
  assert.deepEqual(names.filter(name => name.startsWith('gear-')), ['gear-BOARD-CAPTAIN.png', 'gear-BOARD-GEAR_GEEK.png', 'gear-BOARD-GRINDER.png', 'gear-BOARD-HOT_PACK.png', 'gear-BOARD-HYPE.png', 'gear-BOARD-MOOD_MAKER.png', 'gear-BOARD-RESORT_INSIDER.png', 'gear-BOARD-RESORT_OUTSIDER.png', 'gear-SKI.png']);
  assert.deepEqual(names.filter(name => name.startsWith('og-')), ['og-group.png', 'og-test.png']);
  assert.deepEqual(names.filter(name => !name.startsWith('gear-') && !name.startsWith('og-')), ['CAPTAIN', 'GEAR_GEEK', 'GRINDER', 'HOT_PACK', 'HYPE', 'MOOD_MAKER', 'RESORT_INSIDER', 'RESORT_OUTSIDER'].map(name => `${name}.png`));
  for (const name of names) assert.ok(!forbidden.test(fs.readFileSync(path.join(directory, name), 'latin1')), name);
});

test('브라우저로 나가는 빌드 산출물에 유형 이름이 없다', { skip: !fs.existsSync(path.join(frontend, '.next/static')) && '빌드 산출물이 없어 건너뜀' }, () => {
  assert.deepEqual(hits(walk(path.join(frontend, '.next/static'))), []);
  const pages = walk(path.join(frontend, '.next/server/app/character-test'));
  assert.deepEqual(hits(pages), []);
});

test('확정한 예외 이름만 있고 질문 묶음 이름은 화면 코드에 없다', () => {
  const data = fs.readFileSync(path.join(frontend, 'app/character-test/data.ts'), 'utf8');
  assert.ok(data.includes('대문자 P 대장님'));
  assert.ok(!/E\s*\/\s*I|S\s*\/\s*N|T\s*\/\s*F|J\s*\/\s*P/.test(data));
});
