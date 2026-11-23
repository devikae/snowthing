import type { Metadata } from "next";
import SeasonPassCalculator from "./SeasonPassCalculator";
import { publicPageMetadata } from "../../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata("시즌권 본전 계산기, 방문 목표와 1회 비용", "시즌권 실제 결제액과 할인 리프트권 가격을 비교해 본전 방문 횟수와 1회 비용을 계산합니다.", "/tools/season-pass");
export default function SeasonPassPage() { return <SeasonPassCalculator />; }
