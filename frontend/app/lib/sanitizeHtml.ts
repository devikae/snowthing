import DOMPurify from "dompurify";

// Toast UI 3.2.2에 들어 있는 DOMPurify 2.3.3은 공개된 mXSS 우회가 있어 최신 버전으로 정화한다.
// 허용 범위는 Toast UI 기본 정화기와 같게 둔다.
// 브라우저와 서버(글 상세 첫 HTML)가 같은 규칙을 쓰도록 규칙은 이 파일 한 곳에만 둔다.
const FORBID_TAGS = ["input", "script", "textarea", "form", "button", "select", "meta", "style", "link", "title", "object", "base", "iframe"];

type Purifier = typeof DOMPurify;

/** 주어진 DOMPurify 인스턴스에 링크 훅을 달고, 같은 규칙으로 정화하는 함수를 돌려준다. */
export function createHtmlSanitizer(purify: Purifier) {
  purify.addHook("afterSanitizeAttributes", (node) => {
    if (node.tagName === "A" && node.getAttribute("target")) {
      node.setAttribute("rel", "noopener noreferrer");
    }
  });
  return (html: string) =>
    purify.sanitize(html, {
      ADD_ATTR: ["rel", "target", "hreflang", "type"],
      FORBID_TAGS,
    });
}

let browserSanitizer: ((html: string) => string) | null = null;

export function sanitizeHtml(html: string): string {
  if (!DOMPurify.isSupported) return DOMPurify.sanitize(html);
  browserSanitizer ??= createHtmlSanitizer(DOMPurify);
  return browserSanitizer(html);
}
