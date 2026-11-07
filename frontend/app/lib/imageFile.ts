// 백엔드 spring.servlet.multipart.max-file-size와 같은 값이다. 넘으면 서버가 연결을 끊어 원인을 알 수 없게 된다.
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

export const OVERSIZED_IMAGE_MESSAGE = "사진은 장당 5MB까지 올릴 수 있습니다.";

export function isOversizedImage(file: File) {
  return file.size > MAX_IMAGE_BYTES;
}
