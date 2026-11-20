/**
 * 답글 목록을 화면에서 합칠 때 쓰는 도우미.
 * 답글 id는 작성 순으로 커지므로 id 순으로 정렬해 두면 방금 단 답글과 더보기로 불러온 답글이 섞여도 순서가 맞는다.
 */
type Reply = { commentId: string | number };

function byId(a: Reply, b: Reply) {
  return Number(a.commentId) - Number(b.commentId);
}

export function mergeReplies<T extends Reply>(current: T[], added: T[]): T[] {
  const seen = new Set<string>();
  return [...current, ...added]
    .filter((reply) => {
      const key = String(reply.commentId);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort(byId);
}

/**
 * 더보기 커서는 서버에서 받은 답글 중 마지막 것이어야 한다.
 * 방금 단 답글(localIds)을 커서로 쓰면 그 사이에 있는 답글을 건너뛴다.
 */
export function replyCursor<T extends Reply>(replies: T[], localIds: Set<string>): string | null {
  const loaded = replies.filter((reply) => !localIds.has(String(reply.commentId)));
  const last = loaded.at(-1);
  return last ? String(last.commentId) : null;
}

/** 답글의 답글을 달 때 입력창에 미리 넣는 호출 문구. */
export function mentionPrefix(nickname: string) {
  return `@${nickname} `;
}
