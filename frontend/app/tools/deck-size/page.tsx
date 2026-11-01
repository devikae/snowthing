import type { Metadata } from "next";
import DeckSizeCalculator from "./DeckSizeCalculator";
import { publicPageMetadata } from "../../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata("데크 사이즈 계산기 · 체중·한국 부츠 사이즈", "체중과 한국 부츠 사이즈(mm)로 데크 길이의 참고 범위를 확인하세요. 짧은 길이부터 긴 길이까지 트릭·올라운드·파크·라이딩 선택 기준을 안내합니다.", "/tools/deck-size");
export default function DeckSizePage() { return <DeckSizeCalculator />; }
