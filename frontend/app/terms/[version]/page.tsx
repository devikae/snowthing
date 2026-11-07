import type { Metadata } from "next";
import { notFound } from "next/navigation";
import PrivacyDocument from "../../privacy/PrivacyDocument";
import { termsDocuments } from "../../lib/registrationPolicy";

// 지난 버전은 열람용이다. 검색에는 현재 문서(/terms)만 나오게 한다.
export const metadata: Metadata = { title: "이용약관 이전 버전 | 눈팅 SnowThing", robots: { index: false, follow: true } };

export function generateStaticParams() {
  return Object.keys(termsDocuments).map(version=>({version}));
}
export default async function TermsVersionPage({params}:{params:Promise<{version:string}>}) {
  const {version}=await params;
  const terms=Object.hasOwn(termsDocuments,version)?termsDocuments[version]:undefined;
  if(!terms)notFound();
  return <PrivacyDocument policy={terms} />;
}
