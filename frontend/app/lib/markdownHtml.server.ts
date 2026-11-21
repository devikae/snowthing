import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import DOMPurify from "dompurify";
import { createHtmlSanitizer } from "./sanitizeHtml";

// 글 본문(Markdown)을 서버에서 HTML로 만들어 첫 HTML에 넣는다. 서버 컴포넌트에서만 부른다.
// 브라우저의 ToastViewer와 같은 HTML이 나와야 뷰어가 이어 받을 때 화면이 밀리지 않는다.
// 그래서 다른 Markdown 라이브러리를 쓰지 않고, 브라우저가 쓰는 Toast UI 뷰어 배포 파일을 jsdom 창 안에서 그대로 돌린다.
// 정화는 브라우저와 같은 규칙(sanitizeHtml.ts)을 jsdom 창에 묶은 DOMPurify로 한다. 백엔드는 저장할 때 본문을 정화하지 않는다.
// jsdom은 실행 시점에 node_modules에서 직접 불러온다. import로 쓰면 Turbopack이 .next/node_modules에 빌드 폴더 기준 상대 링크를 만드는데,
// Dockerfile 실행 단계는 .next를 다른 위치(/app)로 옮겨 그 링크가 깨진다. 실행 단계도 node_modules 전체를 복사하므로 직접 불러오면 된다.
// jsdom 창은 runScripts "outside-only"라 본문 안의 script와 on* 속성은 실행되지 않고, resources를 켜지 않아 이미지도 받지 않는다.

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

function getRenderer(): Renderer {
  if (renderer) return renderer;
  const require = createRequire(path.join(process.cwd(), "package.json"));
  const { JSDOM } = require("jsdom") as typeof import("jsdom");
  const window = new JSDOM("<!doctype html><html><body></body></html>", { runScripts: "outside-only" }).window;
  window.eval(readFileSync(require.resolve("@toast-ui/editor/dist/toastui-editor-viewer"), "utf8"));
  const Viewer = (window as unknown as { toastui: { Editor: ViewerConstructor } }).toastui.Editor;
  const sanitize = createHtmlSanitizer(DOMPurify(window as unknown as Parameters<typeof DOMPurify>[0]));
  renderer = { document: window.document, Viewer, sanitize };
  return renderer;
}

/** 본문을 정화된 HTML로 만든다. 실패하면 null을 돌려 화면이 기존 평문 본문으로 그리게 한다. */
export function renderMarkdownHtml(markdown: string | null | undefined): string | null {
  if (!markdown) return null;
  try {
    const { document, Viewer, sanitize } = getRenderer();
    const el = document.createElement("div");
    const viewer = new Viewer({ el, initialValue: markdown, usageStatistics: false, customHTMLSanitizer: sanitize });
    const html = el.innerHTML;
    viewer.destroy();
    return html;
  } catch (error) {
    console.error("글 본문 서버 렌더링 실패", error);
    return null;
  }
}
