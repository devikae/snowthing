import { readFileSync } from "node:fs";
import path from "node:path";
import DOMPurify from "dompurify";
import { createHtmlSanitizer } from "./sanitizeHtml";

// 글 본문(Markdown)을 서버에서 HTML로 만들어 첫 HTML에 넣는다. 서버 컴포넌트에서만 부른다.
// 브라우저의 ToastViewer와 같은 HTML이 나와야 뷰어가 이어 받을 때 화면이 밀리지 않는다.
// 그래서 다른 Markdown 라이브러리를 쓰지 않고, 브라우저가 쓰는 Toast UI 뷰어 배포 파일을 jsdom 창 안에서 그대로 돌린다.
// 정화는 브라우저와 같은 규칙(sanitizeHtml.ts)을 jsdom 창에 묶은 DOMPurify로 한다. 백엔드는 저장할 때 본문을 정화하지 않는다.
// 실행 이미지는 Next standalone 출력이라 빌드가 추적한 파일만 들어간다(IKA-105).
// jsdom은 import로 써야 추적된다. 실행 시점에 require로 부르면 jsdom과 그 의존성이 이미지에서 빠져 매번 실패한다.
// 맨 위 import가 아니라 준비 함수 안의 import()로 부른다. 그래야 jsdom을 못 불러도 글 상세가 500이 되지 않고 평문 본문으로 나간다.
// 뷰어 배포 파일은 코드가 아니라 글자로 읽으므로 next.config.ts의 outputFileTracingIncludes에 경로를 적어 함께 담는다.
// 두 가지가 실제 이미지에 들어 있는지는 scripts/check-body-ssr.mjs가 CI에서 확인한다.
// jsdom 창은 runScripts "outside-only"라 본문 안의 script와 on* 속성은 실행되지 않고, resources를 켜지 않아 이미지도 받지 않는다.

// next.config.ts의 outputFileTracingIncludes와 같은 경로여야 한다. standalone 서버도 frontend 폴더를 작업 폴더로 쓴다.
const VIEWER_FILE = "node_modules/@toast-ui/editor/dist/toastui-editor-viewer.js";

interface ViewerInstance {
  destroy(): void;
}
type ViewerConstructor = new (options: Record<string, unknown>) => ViewerInstance;

interface Renderer {
  document: Document;
  Viewer: ViewerConstructor;
  sanitize: (html: string) => string;
}

let renderer: Renderer | null = null;
// 준비 실패(파일 누락 등)는 다시 해도 같은 결과라 한 번만 시도한다. 요청마다 다시 시도하면 로그만 쌓인다.
let rendererFailed = false;

async function getRenderer(): Promise<Renderer | null> {
  if (renderer) return renderer;
  if (rendererFailed) return null;
  try {
    const { JSDOM } = await import("jsdom");
    const window = new JSDOM("<!doctype html><html><body></body></html>", { runScripts: "outside-only" }).window;
    window.eval(readFileSync(path.join(/* turbopackIgnore: true */ process.cwd(), VIEWER_FILE), "utf8"));
    const Viewer = (window as unknown as { toastui: { Editor: ViewerConstructor } }).toastui.Editor;
    const sanitize = createHtmlSanitizer(DOMPurify(window as unknown as Parameters<typeof DOMPurify>[0]));
    renderer = { document: window.document, Viewer, sanitize };
    return renderer;
  } catch (error) {
    rendererFailed = true;
    // 여기서는 본문을 다루지 않으므로 오류 전체를 남겨도 본문이 로그에 들어가지 않는다.
    console.error("글 본문 서버 렌더링 준비 실패: 서버를 다시 띄울 때까지 모든 글이 평문 본문으로 나간다", error);
    return null;
  }
}

// 특정 글에서만 나는 실패는 처음 한 번과 그 뒤 10분에 한 번만 남긴다. 오류 메시지에 본문 조각이 섞일 수 있어 오류 종류만 적는다.
const RENDER_FAILURE_LOG_INTERVAL_MS = 10 * 60 * 1000;
let lastRenderFailureLogAt = 0;
let renderFailuresSinceLog = 0;

function logRenderFailure(error: unknown) {
  renderFailuresSinceLog += 1;
  const now = Date.now();
  if (lastRenderFailureLogAt && now - lastRenderFailureLogAt < RENDER_FAILURE_LOG_INTERVAL_MS) return;
  const kind = error instanceof Error ? error.name : typeof error;
  console.error(`글 본문 서버 렌더링 실패: 오류 종류 ${kind}, 지난 기록 뒤 ${renderFailuresSinceLog}건`);
  lastRenderFailureLogAt = now;
  renderFailuresSinceLog = 0;
}

/** 본문을 정화된 HTML로 만든다. 실패하면 null을 돌려 화면이 기존 평문 본문으로 그리게 한다. */
export async function renderMarkdownHtml(markdown: string | null | undefined): Promise<string | null> {
  if (!markdown) return null;
  const ready = await getRenderer();
  if (!ready) return null;
  try {
    const { document, Viewer, sanitize } = ready;
    const el = document.createElement("div");
    const viewer = new Viewer({ el, initialValue: markdown, usageStatistics: false, customHTMLSanitizer: sanitize });
    const html = el.innerHTML;
    viewer.destroy();
    return html;
  } catch (error) {
    logRenderFailure(error);
    return null;
  }
}
