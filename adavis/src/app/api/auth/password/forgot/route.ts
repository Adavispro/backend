import { authServiceEndpoints } from "@/features/auth/api/auth.server";
import { forgotPasswordRequestSchema } from "@/features/auth/schemas";
import type {
  ForgotPasswordRequest,
  PasswordResetResponse,
} from "@/features/auth/api/types";
import { proxyPasswordRequest, readJsonBody } from "../_utils";

export async function POST(request: Request) {
  return proxyPasswordRequest<PasswordResetResponse, ForgotPasswordRequest>(
    authServiceEndpoints.forgotPassword,
    {
      body: await readJsonBody(request),
      invalidMessage: "Invalid forgot password request.",
      schema: forgotPasswordRequestSchema,
    },
  );
}
