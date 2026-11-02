import { notFound } from "next/navigation";
import PrivacyDocument from "../PrivacyDocument";
import { registrationPolicies } from "../../lib/registrationPolicy";

export function generateStaticParams() {
  return Object.keys(registrationPolicies).map(version=>({version}));
}
export default async function PrivacyVersionPage({params}:{params:Promise<{version:string}>}) {
  const {version}=await params;
  const policy=Object.hasOwn(registrationPolicies,version)?registrationPolicies[version]:undefined;
  if(!policy)notFound();
  return <PrivacyDocument policy={policy} />;
}
