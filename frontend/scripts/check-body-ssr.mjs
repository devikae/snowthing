// 글 상세 본문이 서버에서 실제로 HTML로 그려지는지 확인한다(CI용, 바깥 API에 기대지 않는다).
// 서버 렌더링에 필요한 파일(jsdom과 그 의존성, Toast UI 뷰어 배포 파일)이 standalone 출력이나 실행 이미지에서 빠지면
// 화면은 평문 본문으로 조용히 바뀌고 빌드도 통과한다. 그래서 빌드 결과물을 직접 띄워 첫 HTML을 본다.
//
// 쓰는 법 (둘 다 NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:<포트> 로 빌드한 결과물이어야 한다. 이 스크립트가 그 포트에 가짜 백엔드를 띄운다)
//   node scripts/check-body-ssr.mjs                          : .next/standalone 서버를 직접 띄워 확인
//   BODY_SSR_TARGET_URL=http://127.0.0.1:3199 node scripts/check-body-ssr.mjs : 이미 떠 있는 서버(실행 이미지 컨테이너)를 확인
// Linux(CI) 기준이다. Windows에서는 standalone 안의 링크를 복사할 권한이 없어 첫 번째 방법이 실패할 수 있다.
import { spawn } from "node:child_process";
import { cpSync, existsSync, mkdtempSync, rmSync } from "node:fs";
import { createServer } from "node:http";
import os from "node:os";
import path from "node:path";

const POST_ID = "00000000-0000-4000-8000-000000000001";
const CONTENT = [
  "첫 문단입니다.",
  "![사진](https://example.com/photo.png)",
  "[링크](https://example.com/page)",
  '<img src="https://example.com/raw.png" onerror="alert(1)">',
  "<script>alert(1)</script>",
].join("\n\n");
const POST = {
  publicId: POST_ID,
  categoryName: "자유게시판",
  categoryCode: "FREE",
  title: "본문 서버 렌더링 점검",
  content: CONTENT,
  status: "NORMAL",
  isAnonymous: false,
  isAuthor: false,
  requiresPassword: false,
  viewCount: 0,
  commentCount: 0,
  likeCount: 0,
  dislikeCount: 0,
  activeReactionTypes: [],
  scrapped: false,
  writer: { publicId: "00000000-0000-4000-8000-000000000002", nickname: "점검", profileImageUrl: null },
  images: [],
  createdAt: "2026-01-01T00:00:00",
};

const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
const apiPort = Number(new URL(apiBase || "http://invalid").port);
if (!/^http:\/\//.test(apiBase) || !apiPort) {
  console.error("NEXT_PUBLIC_API_BASE_URL을 http://127.0.0.1:<포트> 로 두고 빌드한 뒤 같은 값으로 실행해야 합니다.");
  process.exit(2);
}

const backend = createServer((request, response) => {
  const found = request.url === `/api/v1/posts/${POST_ID}`;
  response.writeHead(found ? 200 : 404, { "Content-Type": "application/json" });
  response.end(JSON.stringify(found ? POST : { message: "not found" }));
});
await new Promise((resolve) => backend.listen(apiPort, resolve));

let server = null;
let copiedRoot = null;
let serverOutput = "";
let targetUrl = process.env.BODY_SSR_TARGET_URL;
if (!targetUrl) {
  const standaloneRoot = path.join(process.cwd(), ".next", "standalone");
  if (!existsSync(path.join(standaloneRoot, "frontend", "server.js"))) {
    console.error(`standalone 출력이 없습니다: ${standaloneRoot}. 먼저 npm run build 를 실행하세요.`);
    process.exit(2);
  }
  // 빌드 폴더 안에서 그대로 띄우면 node가 위쪽 폴더의 frontend/node_modules(전체 설치본)까지 찾아 올라가서
  // standalone에서 빠진 패키지가 있어도 통과한다. 실행 이미지처럼 바깥 위치로 옮겨서 띄운다.
  copiedRoot = mkdtempSync(path.join(os.tmpdir(), "body-ssr-"));
  cpSync(standaloneRoot, copiedRoot, { recursive: true, verbatimSymlinks: true });
  const serverFile = path.join(copiedRoot, "frontend", "server.js");
  const port = process.env.BODY_SSR_PORT ?? "3199";
  server = spawn(process.execPath, [serverFile], {
    env: { ...process.env, PORT: port, HOSTNAME: "127.0.0.1", NODE_ENV: "production" },
    stdio: ["ignore", "pipe", "pipe"],
  });
  for (const stream of [server.stdout, server.stderr]) stream.on("data", (chunk) => (serverOutput += chunk));
  targetUrl = `http://127.0.0.1:${port}`;
}

async function fetchDetailHtml() {
  const deadline = Date.now() + 60_000;
  for (;;) {
    try {
      const response = await fetch(`${targetUrl}/posts/${POST_ID}`, { signal: AbortSignal.timeout(10_000) });
      return { status: response.status, html: await response.text() };
    } catch (error) {
      if (Date.now() > deadline) throw error;
      await new Promise((resolve) => setTimeout(resolve, 500));
    }
  }
}

const failures = [];
try {
  const { status, html } = await fetchDetailHtml();
  // 본문은 RSC 데이터에도 원문 그대로 실린다. 서버가 그린 본문 영역만 잘라서 본다.
  const start = html.indexOf('<div class="toastui-editor-contents"');
  const body = start < 0 ? "" : html.slice(start, html.indexOf("</section>", start));
  if (status !== 200) failures.push(`응답 코드가 200이 아니다: ${status}`);
  if (!body) failures.push("서버가 그린 본문 영역(toastui-editor-contents)이 첫 HTML에 없다. 평문 대체 경로로 떨어졌다.");
  else {
    if (!/<p[ >]/.test(body)) failures.push("본문에 <p>가 없다.");
    if (!body.includes("첫 문단입니다.")) failures.push("본문 글자가 없다.");
    if (!body.includes('src="https://example.com/photo.png"')) failures.push("Markdown 사진이 img로 그려지지 않았다.");
    if (!body.includes('href="https://example.com/page"')) failures.push("Markdown 링크가 a로 그려지지 않았다.");
    if (!body.includes('src="https://example.com/raw.png"')) failures.push("원시 HTML img가 남지 않았다.");
    if (/onerror|<script/i.test(body)) failures.push("정화가 동작하지 않았다(onerror 또는 script가 남았다).");
  }
} catch (error) {
  failures.push(`글 상세 요청 실패: ${error}`);
} finally {
  server?.kill();
  backend.close();
}
if (copiedRoot) {
  try {
    rmSync(copiedRoot, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
  } catch {
    // 임시 폴더를 못 지워도 점검 결과와는 무관하다.
  }
}

if (failures.length > 0) {
  console.error("글 본문 서버 렌더링 점검 실패");
  for (const failure of failures) console.error(`- ${failure}`);
  if (serverOutput) console.error(`서버 출력:\n${serverOutput}`);
  process.exit(1);
}
console.log("글 본문 서버 렌더링 점검 통과: 첫 HTML에 문단, 사진, 링크가 있고 정화가 동작한다.");
process.exit(0);
