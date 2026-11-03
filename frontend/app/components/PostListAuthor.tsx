import Image from "next/image";

interface PostListAuthorProps {
  nickname: string;
  profileImageUrl?: string | null;
}

export function PostListAuthor({ nickname, profileImageUrl }: PostListAuthorProps) {
  const anonymousIp = /^익명\s*\((\d{1,3}\.\d{1,3})\.\*{3}\.\*{3}\)$/.exec(nickname)?.[1];
  return (
    <span className={`m-author${anonymousIp ? " m-author-anonymous" : ""}`}>
      {anonymousIp ? <span className="m-anonymous-badge">익명</span> : profileImageUrl ? <Image className="m-avatar" src={profileImageUrl} alt="" width={18} height={18} unoptimized /> : <span className="m-avatar" aria-hidden="true">{nickname?.trim().charAt(0) || "익"}</span>}
      <b>{anonymousIp || nickname}</b>
    </span>
  );
}
