import { fetchPublicJson } from "../lib/publicApi";
import CarpoolListClient from "./CarpoolListClient";
import { carpoolListQuery, normalizeCarpoolPage, readCarpoolPage, type CarpoolPageResponse } from "./listQuery";

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

function first(value: string | string[] | undefined) {
  return (Array.isArray(value) ? value[0] : value) ?? null;
}

// 첫 목록을 서버에서 받아 HTML에 넣는다. 게시판 목록(posts/page.tsx)과 같은 방식이다.
// 검색 결과는 서버에서 받지 않는다. 백엔드의 검색 횟수 제한은 요청한 IP 기준이라,
// 서버가 대신 부르면 모든 방문자의 검색이 프론트 서버 IP 하나로 묶인다.
export default async function CarpoolPage({ searchParams }: Props) {
  const raw = await searchParams;
  if ((first(raw.keyword) ?? "").trim()) return <CarpoolListClient initial={null} />;
  const query = carpoolListQuery(readCarpoolPage(first(raw.page)), "");
  const data = await fetchPublicJson<CarpoolPageResponse>(`/carpools?${query}`);
  return <CarpoolListClient initial={data ? { query, data: normalizeCarpoolPage(data) } : null} />;
}
