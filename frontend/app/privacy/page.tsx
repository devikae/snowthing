import PrivacyDocument from "./PrivacyDocument";
import type { Metadata } from "next";
import { registrationPolicy } from "../lib/registrationPolicy";
export const metadata: Metadata = { title: "개인정보처리방침 | 눈팅 SnowThing" };
export default function PrivacyPage() { return <PrivacyDocument policy={registrationPolicy} />; }
