import { cookies } from "next/headers";

// 서버의 비로그인 조회가 404여도 로그인한 사람에게는 보일 수 있는 글이 있다(1:1 문의, 관리자가 가린 글).
// 로그인 세션 쿠키나 로그인 유지 쿠키가 있으면 404 페이지로 끊지 않고 브라우저가 다시 부르게 둔다.
export async function hasSessionCookie() {
  const store = await cookies();
  // 세션이 끝나도 로그인 유지 쿠키가 있으면 브라우저 요청은 다시 로그인된 채로 간다.
  return store.has("JSESSIONID") || store.has("SESSION") || store.has("SNOWTHING_REMEMBER_ME");
}
