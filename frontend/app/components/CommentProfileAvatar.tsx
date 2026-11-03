import Image from "next/image";

interface CommentProfileAvatarProps {
  nickname: string;
  profileImageUrl: string | null | undefined;
  className?: string;
  size?: number;
}

export function CommentProfileAvatar({
  nickname,
  profileImageUrl,
  className = "comment-profile-avatar",
  size = 38,
}: CommentProfileAvatarProps) {
  if (!profileImageUrl) {
    return (
      <svg
        className={className}
        width={size}
        height={size}
        viewBox="0 0 32 32"
        fill="none"
        role="img"
        aria-label={`${nickname} 프로필`}
        style={{ borderRadius: "50%", flexShrink: 0 }}
      >
        <rect width="32" height="32" rx="16" fill="currentColor" fillOpacity="0.08" />
        <circle cx="16" cy="12.5" r="4.75" fill="currentColor" fillOpacity="0.45" />
        <path
          d="M7.5 27.5C7.5 22.8056 11.3056 19 16 19C20.6944 19 24.5 22.8056 24.5 27.5"
          fill="currentColor"
          fillOpacity="0.45"
        />
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
