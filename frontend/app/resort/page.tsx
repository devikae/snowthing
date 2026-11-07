import { redirect } from "next/navigation";

// 예전 리조트 화면은 하드코딩된 날씨와 혼잡도를 보여 줬다. 실제 정보가 있는 슬로프캠으로 보낸다.
export default function ResortPage() {
  redirect("/resort-cam");
}
