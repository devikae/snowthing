// 슬로프캠 프록시의 Wowza 접속 번호 처리와 비발디 플레이어의 영상 조각 처리 재발 방지 테스트 (IKA-100)
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require('../frontend/node_modules/typescript');

/** 테스트마다 새 모듈을 읽어 접속 번호 저장소가 섞이지 않게 한다. */
function loadWowza() {
  const source = fs.readFileSync(path.join(__dirname, '../frontend/app/stream-proxy/wowzaSession.ts'), 'utf8');
  const context = { exports: {}, Response };
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText, context);
  return context.exports;
}

/** 원본 Wowza 흉내: 재생 목록을 받을 때마다 새 번호를 발급하고, 발급한 번호만 받아 준다. */
function fakeWowza() {
  let next = 100;
  const issued = new Set();
  const calls = [];
  const fetcher = async (url) => {
    calls.push(url);
    if (url.endsWith('/playlist.m3u8')) {
      const session = `w${next++}`;
      issued.add(session);
      return new Response(`#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nchunklist_${session}.m3u8\n`);
    }
    const session = url.match(/_(w\d+)[._]/)?.[1];
    return issued.has(session) ? new Response('ok') : new Response('forbidden', { status: 403 });
  };
  const source = (key = 'high1:2') => ({
    key,
    playlistUrl: async () => `http://origin/${key}/playlist.m3u8`,
    fileUrl: async (file) => `http://origin/${key}/${file}`,
  });
  return { fetcher, calls, issued, source, expire: (session) => issued.delete(session) };
}

test('원본 재생 목록이 발급한 번호를 읽고, 시청자 이름과 원본 이름을 서로 바꾼다', () => {
  const { issuedWowzaSession, toUpstreamWowzaFile, toPublicWowzaManifest } = loadWowza();
  assert.equal(issuedWowzaSession('#EXTM3U\nchunklist_w557468957.m3u8?e=1&h=x\n'), 'w557468957');
  assert.equal(issuedWowzaSession('#EXTM3U\nindex.m3u8\n'), null);
  assert.equal(toUpstreamWowzaFile('chunklist.m3u8', 'w42'), 'chunklist_w42.m3u8');
  assert.equal(toUpstreamWowzaFile('chunklist_w1.m3u8', 'w42'), 'chunklist_w42.m3u8');
  assert.equal(toUpstreamWowzaFile('media_7.ts', 'w42'), 'media_w42_7.ts');
  assert.equal(toUpstreamWowzaFile('media_w1_7.ts', 'w42'), 'media_w42_7.ts');
  assert.equal(toUpstreamWowzaFile('playlist.m3u8', 'w42'), 'playlist.m3u8');
  // 시청자 주소에는 번호와 토큰이 없어야 CDN 캐시를 함께 쓴다.
  assert.equal(
    toPublicWowzaManifest('chunklist_w42.m3u8?e=1&h=x\nmedia_w42_7.ts?e=1\n'),
    'chunklist.m3u8\nmedia_7.ts\n',
  );
});

test('발급받은 번호로 요청하고, 같은 스트림의 다음 요청과 동시 요청은 번호를 함께 쓴다', async () => {
  const { fetchWowzaFile } = loadWowza();
  const wowza = fakeWowza();
  const [a, b] = await Promise.all([
    fetchWowzaFile(wowza.source(), 'chunklist.m3u8', wowza.fetcher),
    fetchWowzaFile(wowza.source(), 'media_7.ts', wowza.fetcher),
  ]);
  const c = await fetchWowzaFile(wowza.source(), 'media_8.ts', wowza.fetcher);
  assert.deepEqual([a.status, b.status, c.status], [200, 200, 200]);
  assert.equal(wowza.calls.filter((url) => url.endsWith('/playlist.m3u8')).length, 1);
  assert.ok(wowza.calls.includes('http://origin/high1:2/chunklist_w100.m3u8'));
  assert.ok(wowza.calls.includes('http://origin/high1:2/media_w100_8.ts'));
  assert.ok(!wowza.calls.some((url) => url.includes('_w1.') || url.includes('_w1_')), '고정 번호 w1을 쓰면 안 된다');
});

test('번호가 만료돼 403이면 새 번호를 받아 한 번 다시 요청한다', async () => {
  const { fetchWowzaFile } = loadWowza();
  const wowza = fakeWowza();
  let forbidden = 0;
  const source = { ...wowza.source(), onForbidden: () => forbidden++ };
  await fetchWowzaFile(source, 'chunklist.m3u8', wowza.fetcher);
  wowza.expire('w100');
  const response = await fetchWowzaFile(source, 'chunklist.m3u8', wowza.fetcher);
  assert.equal(response.status, 200);
  assert.equal(forbidden, 1);
  assert.equal(wowza.calls.at(-1), 'http://origin/high1:2/chunklist_w101.m3u8');
});

test('다시 받은 번호도 403이면 더 반복하지 않고 403을 돌려준다', async () => {
  const { fetchWowzaFile } = loadWowza();
  const calls = [];
  const fetcher = async (url) => {
    calls.push(url);
    return url.endsWith('/playlist.m3u8')
      ? new Response('#EXTM3U\nchunklist_w5.m3u8\n')
      : new Response('forbidden', { status: 403 });
  };
  const wowza = fakeWowza();
  const response = await fetchWowzaFile(wowza.source(), 'chunklist.m3u8', fetcher);
  assert.equal(response.status, 403);
  assert.equal(calls.filter((url) => url.endsWith('/playlist.m3u8')).length, 2);
  assert.equal(calls.filter((url) => url.includes('chunklist_w5')).length, 2);
});

test('카메라마다 번호를 따로 받는다', async () => {
  const { fetchWowzaFile } = loadWowza();
  const wowza = fakeWowza();
  await fetchWowzaFile(wowza.source('high1:2'), 'chunklist.m3u8', wowza.fetcher);
  await fetchWowzaFile(wowza.source('high1:4'), 'chunklist.m3u8', wowza.fetcher);
  assert.ok(wowza.calls.includes('http://origin/high1:2/chunklist_w100.m3u8'));
  assert.ok(wowza.calls.includes('http://origin/high1:4/chunklist_w101.m3u8'));
});

test('시청자가 받은 재생 목록의 번호를 기억하되, 이미 쓰는 번호는 바꾸지 않는다', async () => {
  const { fetchWowzaFile, rememberWowzaSession } = loadWowza();
  const wowza = fakeWowza();
  wowza.issued.add('w77');
  rememberWowzaSession('high1:2', '#EXTM3U\nchunklist_w77.m3u8\n');
  rememberWowzaSession('high1:2', '#EXTM3U\nchunklist_w78.m3u8\n');
  await fetchWowzaFile(wowza.source(), 'media_3.ts', wowza.fetcher);
  assert.deepEqual(wowza.calls, ['http://origin/high1:2/media_w77_3.ts']);
});

test('번호 발급에 실패하면 실패를 기억하지 않고 다음 요청에서 다시 받는다', async () => {
  const { fetchWowzaFile } = loadWowza();
  const wowza = fakeWowza();
  let down = true;
  const fetcher = async (url, init) => (down && url.endsWith('/playlist.m3u8') ? new Response('', { status: 502 }) : wowza.fetcher(url, init));
  await assert.rejects(fetchWowzaFile(wowza.source(), 'chunklist.m3u8', fetcher));
  down = false;
  assert.equal((await fetchWowzaFile(wowza.source(), 'chunklist.m3u8', fetcher)).status, 200);
});

// ---- 비발디 플레이어 ----

function loadVivaldi() {
  const context = { module: { exports: {} }, console, setTimeout, clearTimeout, setInterval, clearInterval, TextEncoder, TextDecoder, Uint8Array, DataView, ArrayBuffer, Blob };
  context.module.exports = {};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../frontend/public/vivaldi.js'), 'utf8'), context);
  return context.module.exports;
}

/** 실제 장비에서 받은 형식(fversion 7)의 영상 프레임. 키프레임은 영상 뒤에 다른 데이터가 붙어 온다. */
function videoPacket(enctype, image, trailer = new Uint8Array(0)) {
  const head = 12 + 67;
  const total = head + image.length + trailer.length;
  const bytes = new Uint8Array(total);
  const view = new DataView(bytes.buffer);
  bytes[0] = (10 << 4) | 2; // SVR
  bytes[1] = 1;
  view.setUint16(2, 6); // ServiceStart
  view.setUint32(8, total - 12);
  bytes[13] = 7; // fversion
  bytes[12 + 27] = enctype;
  view.setUint32(12 + 30, 1795117000);
  view.setUint32(12 + 39, image.length + 20); // datasize = VITC(20) + 영상
  bytes.set(image, head);
  bytes.set(trailer, head + image.length);
  return bytes.buffer;
}

test('비발디: H.265 키프레임은 시작 코드부터 잘라 디코더로 보내고, 뒤에 붙은 데이터는 버린다', () => {
  const { WespConnection, EncType } = loadVivaldi();
  const connection = new WespConnection({ host: 'x', port: 1, id: '', pass: '' });
  const received = [];
  connection.onEncodedFrame = (bytes, codec) => received.push({ bytes: [...bytes], codec });
  connection.onFrame = () => assert.fail('압축 영상을 JPEG로 처리하면 안 된다');
  const image = new Uint8Array([0, 0, 0, 1, 0x40, 1, 0xaa, 0, 0, 0, 1, 0x26, 1, 0xbb]); // VPS, IDR
  const trailer = new Uint8Array(40).fill(9);
  const packet = videoPacket(EncType.H265, image, trailer);
  connection._handleServiceStart({ transmit: 2, bodysize: packet.byteLength - 12 }, packet);
  assert.equal(received.length, 1);
  assert.equal(received[0].codec, EncType.H265);
  assert.deepEqual(received[0].bytes, [...image]);
});

test('비발디: JPEG 프레임은 디코더가 있어도 이미지로 그린다', () => {
  const { WespConnection, EncType } = loadVivaldi();
  const connection = new WespConnection({ host: 'x', port: 1, id: '', pass: '' });
  connection.videoRequestMode = 0;
  let encoded = 0;
  const frames = [];
  connection.onEncodedFrame = () => encoded++;
  connection.onFrame = (blob) => frames.push(blob);
  const packet = videoPacket(EncType.JPEG, new Uint8Array([0xff, 0xd8, 1, 2, 0xff, 0xd9]));
  connection._handleServiceStart({ transmit: 2, bodysize: packet.byteLength - 12 }, packet);
  assert.equal(encoded, 0);
  assert.equal(frames.length, 1);
  assert.equal(frames[0].type, 'image/jpeg');
});

test('비발디: JPEG가 없는 장비는 H.265 하위 스트림을 요청한다', () => {
  const { WespConnection, EncType } = loadVivaldi();
  const connection = new WespConnection({ host: 'x', port: 1, id: '', pass: '' });
  connection.onEncodedFrame = () => {};
  connection.state = 'handshaking';
  connection._send = () => {};
  // XU 장비가 실제로 보낸 StreamInfo: 1번 H.265 해상도 11, 2번 H.265 해상도 4
  const body = new Uint8Array(4 + 40);
  body.set([4, 2, 0, 0]);
  body.set([1, 7, 15, 0, 0, 0, 11], 4);
  body.set([2, 7, 15, 0, 0, 0, 4], 24);
  const packet = new Uint8Array(12 + body.length);
  packet.set(body, 12);
  connection._handleStreamInfo({ transmit: 1, bodysize: body.length }, packet.buffer);
  assert.equal(connection.videoEncoding, EncType.H265);
  assert.equal(connection.videoResolution, 4);
  assert.equal(connection.videoRequestMode, 0);
});

test('비발디: 키프레임 판별 (H.265 VPS와 IRAP, H.264 SPS와 IDR)', () => {
  const { isKeyFrame, findStartCode, EncType } = loadVivaldi();
  assert.equal(isKeyFrame(new Uint8Array([0, 0, 0, 1, 0x40, 1]), EncType.H265), true); // VPS(32)
  assert.equal(isKeyFrame(new Uint8Array([0, 0, 1, 0x26, 1]), EncType.H265), true); // IDR_W_RADL(19)
  assert.equal(isKeyFrame(new Uint8Array([0, 0, 0, 1, 0x02, 1]), EncType.H265), false); // TRAIL_R(1)
  assert.equal(isKeyFrame(new Uint8Array([0, 0, 0, 1, 0x67]), EncType.H264), true); // SPS
  assert.equal(isKeyFrame(new Uint8Array([0, 0, 0, 1, 0x41]), EncType.H264), false); // P slice
  assert.equal(findStartCode(new Uint8Array([9, 9, 0, 0, 0, 1, 5]), 0), 2);
  assert.equal(findStartCode(new Uint8Array([9, 9, 9]), 0), -1);
});
