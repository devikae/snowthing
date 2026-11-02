import Image from "next/image";

interface CommentProfileAvatarProps {
  nickname: string;
  profileImageUrl: string | null | undefined;
  className?: string;
  size?: number;
}

export function CommentProfileAvatar({ nickname, profileImageUrl, className = "comment-profile-avatar", size = 38 }: CommentProfileAvatarProps) {
  if (!profileImageUrl) {
    return (
      <svg className={className} width={size} height={size} viewBox="0 0 32 32" fill="currentColor" role="img" aria-label={`${nickname} 프로필`}>
        <circle cx="16" cy="11" r="6" />
        <path d="M5 29v-3a11 11 0 0 1 22 0v3Z" />
      </svg>
    );
  }
  return (
    <Image
      className={className}
      src={profileImageUrl}
      alt={`${nickname} 프로필`}
      width={size}
      height={size}
      unoptimized
    />
  );
}
