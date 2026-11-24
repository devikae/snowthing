// 글 본문의 눈팅 영상 파일(이미지 문법으로 적힌 mp4)을 영상으로 보여 준다(IKA-112).
// 본문 HTML 정화(sanitizeHtml.ts)는 그대로 두고, 정화가 끝난 뒤 브라우저에서 우리 CDN의 정해진 경로와 이름만 영상으로 바꾼다.
// clip: 디시가 GIF를 바꿔 둔 짧은 mp4라 GIF처럼 소리 없이 자동 반복한다. movie: 재생 버튼으로 보는 영상이다.

const CLIP_URL = /^https:\/\/images\.snowthing\.org\/public\/posts\/clips\/(clip|movie)-[0-9a-f-]{36}\.mp4$/;

export type ClipKind = "clip" | "movie";

export function clipKind(src: string): ClipKind | null {
  const match = CLIP_URL.exec(src);
  return match ? (match[1] as ClipKind) : null;
}

export function createClipVideo(document: Document, src: string, kind: ClipKind): HTMLVideoElement {
  const video = document.createElement("video");
  video.className = kind === "clip" ? "post-clip" : "post-movie";
  video.setAttribute("playsinline", "");
  video.setAttribute("preload", "metadata");
  if (kind === "clip") {
    // 자동 재생은 소리가 없어야 브라우저가 허락한다. 속성과 값을 둘 다 넣어야 iOS Safari도 따른다.
    video.muted = true;
    video.setAttribute("muted", "");
    video.setAttribute("autoplay", "");
    video.setAttribute("loop", "");
  } else {
    video.setAttribute("controls", "");
  }
  video.src = src;
  return video;
}

/** 본문 안의 눈팅 영상 파일을 영상으로 바꾼다. 바꾼 개수를 돌려준다. */
export function embedClips(root: HTMLElement): number {
  let count = 0;
  root.querySelectorAll("img").forEach((image) => {
    const src = image.getAttribute("src") ?? "";
    const kind = clipKind(src);
    if (!kind) return;
    image.replaceWith(createClipVideo(root.ownerDocument, src, kind));
    count += 1;
  });
  return count;
}
