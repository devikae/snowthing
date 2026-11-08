export function groupUrl(groupPublicId: string) {
  return new URL(`/character-test/g/${groupPublicId}`, window.location.origin).toString();
}

// 토큰은 URL fragment에만 담는다. fragment는 서버 요청에 실리지 않아 접근 로그와 Referer에 남지 않는다.
export function restoreUrl(groupPublicId: string, token: string) {
  const url = new URL(groupUrl(groupPublicId));
  url.hash = `t=${token}`;
  return url.toString();
}

export function inviteText(groupName: string) {
  return `${groupName} 관계 지도에 들어와 봐. 나랑 어떤 사이로 나올까?`;
}

export function readFragmentToken() {
  return new URLSearchParams(window.location.hash.slice(1)).get("t");
}

export function clearFragment() {
  window.history.replaceState(null, "", window.location.pathname);
}
