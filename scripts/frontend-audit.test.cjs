// 2026-11-20 프론트 전체 점검에서 고친 문제의 재발 방지 테스트.
// 실행: node --test scripts/frontend-audit.test.cjs (frontend/node_modules가 있어야 한다)
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('../frontend/node_modules/typescript');

const frontend = path.join(__dirname, '../frontend');

// TS 파일 하나를 새 실행 환경에 올린다. 오류 객체는 그 환경 안에서 만들어야 instanceof 검사가 실제 브라우저와 같다.
function loadLib(file, globals = {}) {
  const source = fs.readFileSync(path.join(frontend, 'app/lib', file), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const context = vm.createContext({ exports: {}, DOMException, ...globals });
  vm.runInContext(compiled, context);
  return { exports: context.exports, run: (code) => vm.runInContext(code, context) };
}

test('errorText는 브라우저가 만든 영어 오류 문구를 화면에 내보내지 않는다', () => {
  const { run } = loadLib('errorText.ts');
  const fallback = '대체 문구';
  run(`var fb = ${JSON.stringify(fallback)};`);
  // 앞단이 HTML 오류 페이지로 답해 res.json()이 실패한 경우
  assert.equal(run(`(() => { try { JSON.parse('<html>502 Bad Gateway</html>'); } catch (e) { return exports.errorText(e, fb); } })()`), fallback);
  // 연결 실패
  assert.equal(run(`exports.errorText(new TypeError('Failed to fetch'), fb)`), fallback);
  // AbortSignal.timeout 시간 초과
  assert.equal(run(`exports.errorText(new DOMException('signal timed out', 'TimeoutError'), fb)`), fallback);
  // Error가 아닌 값
  assert.equal(run(`exports.errorText('문자열', fb)`), fallback);
  assert.equal(run(`exports.errorText(new Error(''), fb)`), fallback);
  // 화면 코드가 서버 문구로 던진 오류는 그대로 보여 준다
  assert.equal(run(`exports.errorText(new Error('비밀번호가 일치하지 않습니다.'), fb)`), '비밀번호가 일치하지 않습니다.');
});
