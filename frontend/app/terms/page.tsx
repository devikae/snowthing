import PrivacyDocument from "../privacy/PrivacyDocument";
import { termsDocument } from "../lib/registrationPolicy";
export default function TermsPage() { return <PrivacyDocument policy={termsDocument} />; }
