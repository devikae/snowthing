import type { Metadata } from "next";
import { notFound } from "next/navigation";
import PrivacyDocument from "../PrivacyDocument";
import { registrationPolicies } from "../../lib/registrationPolicy";

// 지난 버전은 열람용이다. 검색에는 현재 문서(/privacy)만 나오게 한다.
export const metadata: Metadata = { title: "개인정보처리방침 이전 버전 | 눈팅 SnowThing", robots: { index: false, follow: true } };

export function generateStaticParams() {
  return Object.keys(registrationPolicies).map(version=>({version}));
}
export default async function PrivacyVersionPage({params}:{params:Promise<{version:string}>}) {
  const {version}=await params;
  const policy=Object.hasOwn(registrationPolicies,version)?registrationPolicies[version]:undefined;
  if(!policy)notFound();
  return <PrivacyDocument policy={policy} />;
}
