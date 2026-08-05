import { authServiceEndpoints } from "@/features/auth/api/auth.server";
import type { PasswordPolicy } from "@/features/auth/api/types";
import { proxyPasswordRequest } from "../_utils";

export async function GET() {
  return proxyPasswordRequest<PasswordPolicy, never>(
    authServiceEndpoints.passwordPolicy,
    {
      invalidMessage: "Invalid password policy request.",
      method: "GET",
    },
  );
}
