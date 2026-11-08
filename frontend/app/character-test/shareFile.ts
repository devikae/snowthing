export type ShareOutcome = "shared" | "saved" | "cancelled";

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** 파일 공유를 지원하는 폰은 공유 창을 열고, 아니면 이미지를 저장한다. 공유 창이 권한 문제로 막혀도 저장으로 넘어간다. */
export async function shareOrSaveImage(blob: Blob, filename: string, title: string): Promise<ShareOutcome> {
  const file = new File([blob], filename, { type: "image/png" });
  if (typeof navigator.canShare === "function" && navigator.canShare({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title });
      return "shared";
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return "cancelled";
    }
  }
  downloadBlob(blob, filename);
  return "saved";
}

export type LinkOutcome = "shared" | "copied" | "cancelled" | "failed";

/** 링크 공유 창, 안 되면 클립보드 복사. 둘 다 안 되면 호출한 쪽이 링크를 화면에 보여 준다. */
export async function shareLink(url: string, title: string, text: string): Promise<LinkOutcome> {
  if (typeof navigator.share === "function") {
    try {
      await navigator.share({ title, text, url });
      return "shared";
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return "cancelled";
    }
  }
  try {
    await navigator.clipboard.writeText(url);
    return "copied";
  } catch {
    return "failed";
  }
}

export function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("이미지를 불러오지 못했습니다."));
    image.src = src;
  });
}

export function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("이미지를 만들지 못했습니다."))), "image/png");
  });
}

export async function pageFontFamily() {
  await document.fonts.ready;
  return getComputedStyle(document.body).fontFamily;
}
