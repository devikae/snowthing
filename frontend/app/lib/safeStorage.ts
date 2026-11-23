// 브라우저가 사이트 데이터를 막으면(쿠키 차단 설정, 일부 사파리 개인 정보 보호 모드) localStorage에 손대는 순간 예외가 난다.
// 렌더링이나 effect 안에서 난 예외는 화면 전체를 오류 화면으로 바꾸므로, 저장소는 편의 기능으로만 다루고 실패는 삼킨다.

export function readLocal(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeLocal(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // 저장하지 못해도 이번 화면에서는 고른 값이 그대로 보인다.
  }
}
