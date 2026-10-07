import Image from "next/image";

interface CommentProfileAvatarProps {
  nickname: string;
  profileImageUrl: string | null | undefined;
}

export function CommentProfileAvatar({ nickname, profileImageUrl }: CommentProfileAvatarProps) {
  return (
    <Image
      className="comment-profile-avatar"
      src={profileImageUrl || "/images/default-profile-avatar.png"}
      alt={`${nickname} 프로필`}
      width={38}
      height={38}
      unoptimized={Boolean(profileImageUrl)}
    />
  );
}
