import DOMPurify from "dompurify";

// Toast UI 3.2.2에 들어 있는 DOMPurify 2.3.3은 공개된 mXSS 우회가 있어 최신 버전으로 정화한다.
// 허용 범위는 Toast UI 기본 정화기와 같게 둔다.
const FORBID_TAGS = ["input", "script", "textarea", "form", "button", "select", "meta", "style", "link", "title", "object", "base", "iframe"];

let hookInstalled = false;

function installLinkHook() {
  if (hookInstalled || !DOMPurify.isSupported) return;
  DOMPurify.addHook("afterSanitizeAttributes", (node) => {
    if (node.tagName === "A" && node.getAttribute("target")) {
      node.setAttribute("rel", "noopener noreferrer");
    }
  });
  hookInstalled = true;
}

export function sanitizeHtml(html: string): string {
  installLinkHook();
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ["rel", "target", "hreflang", "type"],
    FORBID_TAGS,
  });
}
