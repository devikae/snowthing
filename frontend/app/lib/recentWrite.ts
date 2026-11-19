// 방금 글을 쓴 브라우저 표시 (IKA-96).
// 상세와 목록의 첫 화면은 서버가 비로그인 기준으로 그린다. 글을 쓴 직후에는 서버 첫 화면 대신 브라우저가 다시 받아
// 작성자 본인 기준 응답(본인 글 포함)을 보게 한다. 모든 작성에 똑같이 붙이므로 이 표시만으로 글 상태를 알 수 없다.
// 글 ID만 담고 1시간 뒤 사라진다. 서버 권한 판단에는 쓰지 않는다.
export const RECENT_WRITE_COOKIE = "snowthing_recent_write";
const MAX_AGE_SECONDS = 60 * 60;

export function markRecentWrite(publicId: string) {
  try {
    const secure = window.location.protocol === "https:" ? "; Secure" : "";
    document.cookie = `${RECENT_WRITE_COOKIE}=${encodeURIComponent(publicId)}; Max-Age=${MAX_AGE_SECONDS}; Path=/; SameSite=Lax${secure}`;
  } catch {
    // 쿠키를 못 써도 글쓰기 흐름은 그대로 간다.
  }
}

export function hasRecentWrite() {
  try {
    return document.cookie.split("; ").some((entry) => entry.startsWith(`${RECENT_WRITE_COOKIE}=`));
  } catch {
    return false;
  }
}
