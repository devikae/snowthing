import type { Metadata } from "next";
import PrivacyDocument from "../privacy/PrivacyDocument";
import { termsDocument } from "../lib/registrationPolicy";
import { publicPageMetadata } from "../lib/siteMetadata";

export const metadata: Metadata = publicPageMetadata("이용약관", "눈팅 SnowThing 서비스 이용 조건, 게시물 신고와 조치, 이용 제한 기준을 안내합니다.", "/terms");

export default function TermsPage() { return <PrivacyDocument policy={termsDocument} />; }
