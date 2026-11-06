import { notFound } from "next/navigation";
import PrivacyDocument from "../../privacy/PrivacyDocument";
import { termsDocuments } from "../../lib/registrationPolicy";

export function generateStaticParams() {
  return Object.keys(termsDocuments).map(version=>({version}));
}
export default async function TermsVersionPage({params}:{params:Promise<{version:string}>}) {
  const {version}=await params;
  const terms=Object.hasOwn(termsDocuments,version)?termsDocuments[version]:undefined;
  if(!terms)notFound();
  return <PrivacyDocument policy={terms} />;
}
