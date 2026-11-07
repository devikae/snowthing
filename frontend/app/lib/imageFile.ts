// 백엔드 spring.servlet.multipart.max-file-size와 같은 값이다. 넘으면 서버가 연결을 끊어 원인을 알 수 없게 된다.
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
// 휴대폰 원본 사진(4000px 이상, 5MB 이상)을 줄일 때의 긴 변 길이. 줄여도 크면 한 번 더 작게 시도한다.
const RESIZE_STEPS = [2048, 1600];
const JPEG_QUALITY = 0.85;

export const OVERSIZED_IMAGE_MESSAGE = "사진은 장당 5MB까지 올릴 수 있습니다.";

export function isOversizedImage(file: Blob) {
  return file.size > MAX_IMAGE_BYTES;
}

function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", JPEG_QUALITY));
}

// 5MB를 넘는 사진은 올리기 전에 브라우저에서 JPEG로 줄인다. 줄일 수 없으면 원본을 돌려주고, 호출한 쪽이 크기 안내를 띄운다.
export async function prepareImageForUpload<T extends Blob>(file: T): Promise<T | File> {
  if (!isOversizedImage(file) || typeof createImageBitmap !== "function") return file;
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    return file;
  }
  try {
    for (const maxSide of RESIZE_STEPS) {
      const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
      const canvas = document.createElement("canvas");
      canvas.width = Math.round(bitmap.width * scale);
      canvas.height = Math.round(bitmap.height * scale);
      const context = canvas.getContext("2d");
      if (!context) return file;
      // JPEG는 투명을 담지 못해 흰 바탕을 깐다.
      context.fillStyle = "#ffffff";
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      const blob = await canvasToBlob(canvas);
      if (blob && !isOversizedImage(blob)) {
        const baseName = file instanceof File ? file.name.replace(/\.[^.]+$/, "") : "image";
        return new File([blob], `${baseName}.jpg`, { type: "image/jpeg" });
      }
    }
    return file;
  } finally {
    bitmap.close();
  }
}
