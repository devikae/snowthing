// 슬로프캠 HLS 카메라 전체 재생 점검 (읽기 전용).
// 화면과 같은 변환(옛 경로 해석, 공식 주소 갱신)을 거친 주소로 재생 목록 → 조각 목록 → 영상 조각을 차례로 요청한다.
//
// 운영과 같은 조건:  node scripts/resort-cam-playback-probe.cjs
// 로컬 프록시 점검:  node scripts/resort-cam-playback-probe.cjs --proxy-origin http://localhost:3120
// 옵션: --api <카메라 목록 API>, --origin <Origin 헤더>, --out <결과 JSON 경로>
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('../frontend/node_modules/typescript');

function option(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index > 0 ? process.argv[index + 1] : fallback;
}
const API = option('api', 'https://snowthing.org/api/v1/resort-cams');
const PROXY_ORIGIN = option('proxy-origin', 'https://cam.snowthing.org');
const PAGE_ORIGIN = option('origin', 'https://snowthing.org');
const OUT = option('out');

function loadSource(file) {
  const source = fs.readFileSync(path.join(__dirname, '../frontend/app/resort-cam', file), 'utf8');
  const context = { exports: {} };
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } });
  vm.runInNewContext(compiled.outputText, context);
  return context.exports;
}
const { resolveCameraSourceUrl } = loadSource('types.ts');
const { updateOfficialCameraSource } = loadSource('sourceUpdates.ts');

async function request(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(12000), headers: { Origin: PAGE_ORIGIN } });
  return response;
}

/** 재생 목록부터 첫 영상 조각까지 따라간다. 막힌 단계와 응답 코드를 남긴다. */
async function probe(sourceUrl) {
  let url = new URL(sourceUrl, PROXY_ORIGIN);
  const crossOrigin = url.origin !== new URL(PAGE_ORIGIN).origin;
  const stages = [];
  try {
    for (let depth = 0; depth < 3; depth++) {
      const kind = depth === 0 ? '재생 목록' : '조각 목록';
      const response = await request(url);
      const text = await response.text();
      const cors = response.headers.get('access-control-allow-origin');
      stages.push({ kind, status: response.status });
      if (!response.ok) return { ok: false, stage: kind, status: response.status, stages };
      if (!text.trimStart().startsWith('#EXTM3U')) return { ok: false, stage: kind, status: 'HLS 아님', stages };
      if (crossOrigin && depth === 0 && cors !== '*' && cors !== PAGE_ORIGIN) {
        return { ok: false, stage: kind, status: 'CORS 차단', stages };
      }
      // 상위 목록은 첫 화질을, 조각 목록은 가장 최근 조각을 고른다(오래된 조각은 원본에서 막 지워졌을 수 있다).
      const uris = text.split(/\r?\n/).map((line) => line.trim()).filter((line) => line && !line.startsWith('#'));
      const next = text.includes('#EXT-X-STREAM-INF') ? uris[0] : uris.at(-1);
      if (!next) return { ok: false, stage: kind, status: '빈 목록', stages };
      url = new URL(next, url);
      if (/\.m3u8(?:$|\?)/i.test(url.pathname + url.search)) continue;
      const segment = await request(url);
      const bytes = segment.ok ? (await segment.arrayBuffer()).byteLength : 0;
      if (!segment.ok) await segment.body?.cancel();
      stages.push({ kind: '영상 조각', status: segment.status, bytes });
      return { ok: segment.ok && bytes > 0, stage: '영상 조각', status: segment.status, stages };
    }
    return { ok: false, stage: '조각 목록', status: '목록 깊이 초과', stages };
  } catch (error) {
    return { ok: false, stage: stages.length ? '다음 단계' : '재생 목록', status: error.name, stages };
  }
}

async function main() {
  // 목록 API가 잠시 응답하지 않을 때를 위해 저장해 둔 응답 파일도 받는다.
  const metadata = /^https?:/.test(API)
    ? await (await fetch(API, { signal: AbortSignal.timeout(15000), headers: { Accept: 'application/json' } })).json()
    : JSON.parse(fs.readFileSync(API, 'utf8'));
  const cameras = metadata.resorts.flatMap((resort) => resort.cameras.map((stored) => {
    const original = { ...stored, sourceUrl: resolveCameraSourceUrl(stored.sourceUrl) };
    return { resort: resort.code, original, camera: updateOfficialCameraSource(resort.code, original) };
  }));
  const results = [];
  const queue = [...cameras];
  await Promise.all(Array.from({ length: 4 }, async () => {
    while (queue.length) {
      const { resort, original, camera } = queue.shift();
      const base = { resort, code: camera.code, type: camera.sourceType, url: camera.sourceUrl };
      if (camera.sourceType === 'HLS') results.push({ ...base, ...(await probe(camera.sourceUrl)) });
      // 공식 페이지에서 보기로 돌린 HLS 카메라는 원본이 다시 나오는지 확인만 한다.
      else if (original.sourceType === 'HLS') results.push({ ...base, ok: null, offAir: await probe(original.sourceUrl) });
      else results.push({ ...base, ok: null });
    }
  }));
  const order = metadata.resorts.map((resort) => resort.code);
  results.sort((a, b) => order.indexOf(a.resort) - order.indexOf(b.resort) || a.code.localeCompare(b.code));

  console.log(`점검 시각 ${new Date().toISOString()} / 목록 ${API} / 프록시 ${PROXY_ORIGIN}\n`);
  console.log('| 리조트 | 유형 | 재생 | 실패 내역 | 공식 페이지로 돌린 카메라 (원본 재확인) |');
  console.log('| --- | --- | ---: | --- | --- |');
  for (const code of order) {
    const items = results.filter((result) => result.resort === code);
    const type = [...new Set(items.map((item) => item.type))].join(', ');
    const offAir = items.filter((item) => item.offAir)
      .map((item) => `${item.code} ${item.offAir.ok ? '원본 재개됨' : `${item.offAir.stage} ${item.offAir.status}`}`).join('<br>') || '없음';
    if (items.every((item) => item.ok === null)) {
      console.log(`| ${code} | ${type} | 점검 안 함 | HLS 아님 | ${offAir} |`);
      continue;
    }
    const failed = items.filter((item) => item.ok === false);
    const detail = failed.map((item) => `${item.code} ${item.stage} ${item.status}`).join('<br>') || '없음';
    console.log(`| ${code} | ${type} | ${items.filter((item) => item.ok).length}/${items.filter((item) => item.ok !== null).length} | ${detail} | ${offAir} |`);
  }
  if (OUT) fs.writeFileSync(OUT, JSON.stringify({ checkedAt: new Date().toISOString(), api: API, proxyOrigin: PROXY_ORIGIN, results }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
