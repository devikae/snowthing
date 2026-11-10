import { AdminBoardPosts } from "../AdminBoardPosts";

export default function AdminMarketPage() {
  return (
    <AdminBoardPosts
      board="MARKET"
      title="중고장터 관리"
      description="시장 글을 공개, 숨김, 차단 상태로 바꾸거나 삭제합니다. 신고로 임시조치된 글은 신고 처리 화면에서 다룹니다."
    />
  );
}
