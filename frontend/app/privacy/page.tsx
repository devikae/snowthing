import PrivacyDocument from "./PrivacyDocument";
import type { Metadata } from "next";
import { registrationPolicy } from "../lib/registrationPolicy";
import { publicPageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata("개인정보처리방침", "눈팅 SnowThing이 처리하는 개인정보 항목, 목적, 보유 기간과 이용자의 권리를 안내합니다.", "/privacy");

export default function PrivacyPage() { return <PrivacyDocument policy={registrationPolicy} />; }
