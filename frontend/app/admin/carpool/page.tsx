import { AdminBoardPosts } from "../AdminBoardPosts";

export default function AdminCarpoolPage() {
  return (
    <AdminBoardPosts
      board="CARPOOL"
      title="카풀 관리"
      description="카풀 글을 블라인드하거나 삭제합니다. 신고로 임시조치된 글은 신고 처리 화면에서 다룹니다."
    />
  );
}
