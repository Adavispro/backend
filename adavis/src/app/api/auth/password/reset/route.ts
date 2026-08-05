import { authServiceEndpoints } from "@/features/auth/api/auth.server";
import { resetPasswordRequestSchema } from "@/features/auth/schemas";
import type { ResetPasswordRequest } from "@/features/auth/api/types";
import { proxyPasswordRequest, readJsonBody } from "../_utils";

export async function POST(request: Request) {
  return proxyPasswordRequest<null, ResetPasswordRequest>(
    authServiceEndpoints.resetPassword,
    {
      body: await readJsonBody(request),
      invalidMessage: "Invalid reset password request.",
      schema: resetPasswordRequestSchema,
    },
  );
}
