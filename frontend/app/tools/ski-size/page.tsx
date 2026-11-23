import type { Metadata } from "next";
import SkiSizeCalculator from "./SkiSizeCalculator";
import { publicPageMetadata } from "../../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata("스키 사이즈 계산기, 키와 체중으로 스키 길이", "키와 체중으로 스키 길이의 참고 범위를 확인하세요. 숏턴과 회전부터 롱턴과 대회전까지 짧은 쪽, 중간, 긴 쪽 선택 기준을 안내합니다.", "/tools/ski-size");
export default function SkiSizePage() { return <SkiSizeCalculator />; }
