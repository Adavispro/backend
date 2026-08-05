import { authServiceEndpoints } from "@/features/auth/api/auth.server";
import { adminResetPasswordRequestSchema } from "@/features/auth/schemas";
import type {
  AdminResetPasswordRequest,
  PasswordResetResponse,
} from "@/features/auth/api/types";
import { proxyPasswordRequest, readJsonBody } from "../_utils";

export async function POST(request: Request) {
  return proxyPasswordRequest<PasswordResetResponse, AdminResetPasswordRequest>(
    authServiceEndpoints.adminResetPassword,
    {
      body: await readJsonBody(request),
      invalidMessage: "Invalid admin reset password request.",
      requiresAuth: true,
      schema: adminResetPasswordRequestSchema,
    },
  );
}
