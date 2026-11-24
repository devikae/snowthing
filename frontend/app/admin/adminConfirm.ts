import type { ConfirmOptions } from "../lib/feedback";

/**
 * 관리자 화면의 확인 문구("...할까요? 결과 설명")를 공통 확인 창 옵션으로 바꾼다.
 * 첫 물음까지가 제목, 나머지가 설명이다. 글 제목을 따옴표로 인용한 문구는 제목 안의 물음표에서 끊기지 않게 닫는 따옴표 뒤에서 찾는다.
 * danger: 삭제, 정지, 차단, 가리기처럼 사용자에게 바로 영향이 가는 조치.
 */
export function ask(message: string, confirmLabel: string, danger = false): ConfirmOptions {
  const from = message.startsWith('"') ? message.lastIndexOf('"') : 0;
  const cut = message.slice(from).search(/\?\s/);
  if (cut < 0) return { title: message, confirmLabel, danger };
  const end = from + cut + 1;
  return { title: message.slice(0, end), description: message.slice(end).trim(), confirmLabel, danger };
}
