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

const ANSWERS_PATTERN = /^[AB]{15}$/;

/** 결과를 다른 브라우저에서 다시 열 때 쓰는 fragment. 서버에 남지 않게 fragment에만 둔다. */
export function resultFragment(answers: string, sport: "SKI" | "BOARD") {
  return `r=${answers}&s=${sport}`;
}

export function readResultFragment(): { answers: string; sport: "SKI" | "BOARD" } | null {
  const params = new URLSearchParams(window.location.hash.slice(1));
  const answers = params.get("r");
  const sport = params.get("s");
  if (!answers || !ANSWERS_PATTERN.test(answers) || (sport !== "SKI" && sport !== "BOARD")) return null;
  return { answers, sport };
}
