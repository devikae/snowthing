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
        <circle cx="16" cy="10.5" r="5.5" />
        <path d="M5.5 26c0-5.2 4.7-9 10.5-9s10.5 3.8 10.5 9c0 2-4.7 3.5-10.5 3.5S5.5 28 5.5 26Z" />
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
