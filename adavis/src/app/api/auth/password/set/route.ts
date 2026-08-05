import { authServiceEndpoints } from "@/features/auth/api/auth.server";
import { setPasswordRequestSchema } from "@/features/auth/schemas";
import type { SetPasswordRequest } from "@/features/auth/api/types";
import { proxyPasswordRequest, readJsonBody } from "../_utils";

export async function POST(request: Request) {
  return proxyPasswordRequest<null, SetPasswordRequest>(
    authServiceEndpoints.setPassword,
    {
      body: await readJsonBody(request),
      invalidMessage: "Invalid set password request.",
      schema: setPasswordRequestSchema,
    },
  );
}
