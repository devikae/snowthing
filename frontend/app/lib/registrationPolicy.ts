import current from "../../../shared/registration-policies/current.json";
import draft from "../../../shared/registration-policies/draft-2026-11-01-v1.json";

// Keep previous snapshots in this registry when publishing the next version.
export const registrationPolicies: Record<string, typeof draft> = { [draft.version]: draft };
export const registrationPolicy = registrationPolicies[current.version];
export const registrationPolicyUrl = `/privacy/${registrationPolicy.version}`;
