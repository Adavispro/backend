import { authServiceEndpoints } from "@/features/auth/api/auth.server";
import { verifyPasswordPolicyRequestSchema } from "@/features/auth/schemas";
import type {
  PasswordPolicyVerification,
  VerifyPasswordPolicyRequest,
} from "@/features/auth/api/types";
import { proxyPasswordRequest, readJsonBody } from "../../_utils";

export async function POST(request: Request) {
  return proxyPasswordRequest<
    PasswordPolicyVerification,
    VerifyPasswordPolicyRequest
  >(authServiceEndpoints.verifyPasswordPolicy, {
    body: await readJsonBody(request),
    invalidMessage: "Invalid password policy verification request.",
    schema: verifyPasswordPolicyRequestSchema,
  });
}
