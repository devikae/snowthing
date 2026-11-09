import { fetchPreview } from "../../../lib/sharePreview";
import type { SharedSeason } from "../../types";

// 결산 페이지, 링크 미리보기 문구, 카드 이미지가 같은 값을 쓴다. fetchPreview가 서버 메모리에 60초 보관해서
// 크롤러가 페이지와 이미지를 연달아 받아도 백엔드는 한 번만 부른다. 공유를 끄면 1분 안에 사라진다.
export function fetchSharedSeason(token: string) {
  if (!/^[0-9a-f-]{36}$/i.test(token)) return Promise.resolve(null);
  return fetchPreview<SharedSeason, SharedSeason>(`/season-shares/${encodeURIComponent(token)}`, (data) => data);
}
