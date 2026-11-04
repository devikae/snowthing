import { CommentProfileAvatar } from "./CommentProfileAvatar";

interface PostListAuthorProps {
  nickname: string;
  profileImageUrl?: string | null;
}

export function PostListAuthor({ nickname, profileImageUrl }: PostListAuthorProps) {
  const anonymousIp = /^익명\s*\((\d{1,3}\.\d{1,3})\.\*{3}\.\*{3}\)$/.exec(nickname)?.[1];
  return (
    <span className={`m-author${anonymousIp ? " m-author-anonymous" : ""}`}>
      {anonymousIp ? (
        <span className="m-anonymous-badge">익명</span>
      ) : (
        <CommentProfileAvatar
          nickname={nickname}
          profileImageUrl={profileImageUrl}
          size={18}
          className="m-avatar"
        />
      )}
      <b>{anonymousIp || nickname}</b>
    </span>
  );
}
