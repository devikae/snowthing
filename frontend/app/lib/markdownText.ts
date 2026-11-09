// 본문 뷰어(Toast UI)는 브라우저에서만 그려진다. 그 전에 보여 줄 평문을 만든다.
// JS를 실행하지 않는 검색엔진과 JS를 끈 방문자도 본문을 읽을 수 있다.
// 결과는 React가 글자로만 넣으므로 HTML로 해석되지 않는다.
export function markdownToPlainText(markdown: string | null | undefined): string {
  if (!markdown) return "";
  return markdown
    .replace(/\r\n?/g, "\n")
    .replace(/!\[[^\]]*\]\([^)]*\)/g, " ")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/(p|div|li|h[1-6])>/gi, "\n")
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, "&")
    .replace(/[#>*_`~|]+/g, " ")
    .replace(/^\s*[-+]\s+/gm, "")
    .replace(/[ \t]+/g, " ")
    .replace(/ *\n */g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}
