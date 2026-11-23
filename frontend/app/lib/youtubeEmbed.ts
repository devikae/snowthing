// 글 본문에서 유튜브 주소만 있는 문단을 유튜브 플레이어로 바꾼다(IKA-112).
// 본문 HTML 정화(sanitizeHtml.ts)는 iframe을 계속 막는다. 플레이어는 정화가 끝난 뒤 브라우저에서,
// 주소에서 꺼낸 영상 ID(11자 영문, 숫자, -, _)만으로 새로 만든다. 글쓴이가 쓴 iframe이나 속성은 그대로 넘어오지 않는다.
// 문장 안에 섞인 유튜브 링크는 링크로 둔다. 한 줄에 주소만 있을 때만 바꿔야 글 흐름이 깨지지 않는다.

const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;
const YOUTUBE_HOSTS = new Set(["youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"]);

export interface YoutubeVideo {
  id: string;
  start: number | null;
}

function parseStart(value: string | null): number | null {
  if (!value) return null;
  const match = /^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s?)?$/.exec(value);
  if (!match || match[0] === "") return null;
  const seconds = Number(match[1] ?? 0) * 3600 + Number(match[2] ?? 0) * 60 + Number(match[3] ?? 0);
  return seconds > 0 ? seconds : null;
}

/** 유튜브 영상 주소면 영상 ID와 시작 초를, 아니면 null을 돌려준다. */
export function parseYoutubeUrl(raw: string): YoutubeVideo | null {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    return null;
  }
  if (url.protocol !== "https:" && url.protocol !== "http:") return null;
  const host = url.hostname.toLowerCase();
  let id: string | null = null;
  if (host === "youtu.be") {
    id = url.pathname.split("/")[1] ?? null;
  } else if (YOUTUBE_HOSTS.has(host)) {
    if (url.pathname === "/watch") id = url.searchParams.get("v");
    else {
      const [, kind, value] = url.pathname.split("/");
      if (kind === "shorts" || kind === "embed" || kind === "live") id = value ?? null;
    }
  }
  if (!id || !VIDEO_ID.test(id)) return null;
  return { id, start: parseStart(url.searchParams.get("t") ?? url.searchParams.get("start")) };
}

/** 문단 하나가 유튜브 주소 한 줄(글자 그대로 또는 주소와 같은 글자의 링크)이면 그 영상. */
function paragraphVideo(paragraph: HTMLParagraphElement): YoutubeVideo | null {
  const text = paragraph.textContent?.trim() ?? "";
  if (!text || /\s/.test(text)) return null;
  const elements = Array.from(paragraph.children);
  if (elements.length > 1) return null;
  if (elements.length === 1) {
    const anchor = elements[0];
    if (!(anchor instanceof HTMLAnchorElement) || anchor.textContent?.trim() !== text) return null;
    return parseYoutubeUrl(anchor.href);
  }
  return parseYoutubeUrl(text);
}

export function youtubeEmbedSrc(video: YoutubeVideo): string {
  const params = new URLSearchParams({ rel: "0" });
  if (video.start) params.set("start", String(video.start));
  return `https://www.youtube-nocookie.com/embed/${video.id}?${params.toString()}`;
}

export function createYoutubePlayer(document: Document, video: YoutubeVideo): HTMLElement {
  const frame = document.createElement("div");
  frame.className = "post-youtube-embed";
  const iframe = document.createElement("iframe");
  iframe.title = "유튜브 영상";
  // 글 하나에 영상이 20개 넘게 있기도 해서 화면 가까이 올 때 불러온다. loading은 src보다 먼저 정해야 처음부터 지연된다.
  iframe.setAttribute("loading", "lazy");
  iframe.setAttribute("allow", "accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen");
  iframe.setAttribute("referrerpolicy", "strict-origin-when-cross-origin");
  iframe.setAttribute("allowfullscreen", "");
  iframe.src = youtubeEmbedSrc(video);
  frame.appendChild(iframe);
  return frame;
}

/** 본문 안의 유튜브 주소 문단을 플레이어로 바꾼다. 바꾼 개수를 돌려준다. */
export function embedYoutubeLinks(root: HTMLElement): number {
  let count = 0;
  root.querySelectorAll("p").forEach((paragraph) => {
    const video = paragraphVideo(paragraph);
    if (!video) return;
    paragraph.replaceWith(createYoutubePlayer(root.ownerDocument, video));
    count += 1;
  });
  return count;
}
