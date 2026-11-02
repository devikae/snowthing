import PrivacyDocument from "./PrivacyDocument";
import { registrationPolicy } from "../lib/registrationPolicy";
export default function PrivacyPage() { return <PrivacyDocument policy={registrationPolicy} />; }
