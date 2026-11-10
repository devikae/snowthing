import type { Metadata } from "next";
import { privatePageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = privatePageMetadata("중고장터", "라이더끼리 보드, 스키 장비와 의류를 사고파는 중고장터입니다. 로그인한 회원만 이용할 수 있어요.");

export default function MarketLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
