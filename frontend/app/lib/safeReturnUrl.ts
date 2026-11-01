/** Accept only paths that stay on this site, including browser backslash handling. */
export function safeReturnUrl(candidate: string | null): string {
  if (!candidate || !candidate.startsWith("/") || candidate.startsWith("//") || /[\\\u0000-\u001f\u007f]/.test(candidate)) return "/";
  try {
    const base = "https://snowthing.invalid";
    const url = new URL(candidate, base);
    return url.origin === base ? url.pathname + url.search + url.hash : "/";
  } catch { return "/"; }
}
