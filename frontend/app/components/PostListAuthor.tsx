interface PostListAuthorProps {
  nickname: string;
}

export function PostListAuthor({ nickname }: PostListAuthorProps) {
  const anonymousIp = /^익명\s*\((\d{1,3}\.\d{1,3})\.\*{3}\.\*{3}\)$/.exec(nickname)?.[1];
  return (
    <span className={`m-author${anonymousIp ? " m-author-anonymous" : ""}`}>
      {anonymousIp ? <span className="m-anonymous-badge">익명</span> : <span className="m-avatar" aria-hidden="true">{nickname?.trim().charAt(0) || "익"}</span>}
      <b>{anonymousIp || nickname}</b>
    </span>
  );
}
