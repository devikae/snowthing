import current from "../../../shared/registration-policies/current.json";
import draftV1 from "../../../shared/registration-policies/draft-2026-11-01-v1.json";
import draftV2 from "../../../shared/registration-policies/draft-2026-11-06-v2.json";
import termsDraftV1 from "../../../shared/registration-policies/terms-draft-2026-11-06-v1.json";

// Keep previous snapshots in these registries when publishing the next version.
export const registrationPolicies: Record<string, typeof draftV2> = {
  [draftV1.version]: draftV1,
  [draftV2.version]: draftV2,
};
export const registrationPolicy = registrationPolicies[current.version];
export const registrationPolicyUrl = `/privacy/${registrationPolicy.version}`;

export const termsDocuments: Record<string, typeof termsDraftV1> = { [termsDraftV1.version]: termsDraftV1 };
export const termsDocument = termsDocuments[current.termsVersion];
export const termsDocumentUrl = `/terms/${termsDocument.version}`;
