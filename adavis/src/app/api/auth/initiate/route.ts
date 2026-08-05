import { NextResponse } from "next/server";
import {
  authErrorResponse,
  authServiceEndpoints,
  authServiceUnavailableResponse,
  requestAuthService,
} from "@/features/auth/api/auth.server";
import {
  firstSchemaError,
  loginInitiateRequestSchema,
  loginInitiateResponseSchema,
} from "@/features/auth/schemas";
import type {
  LoginInitiateRequest,
  LoginInitiateResponse,
} from "@/features/auth/api/types";

export async function POST(request: Request) {
  let body: unknown;

  try {
    body = await request.json();
  } catch {
    return authErrorResponse(400, { message: "Invalid verification request." });
  }

  const parsedRequest = loginInitiateRequestSchema.safeParse(body);
  if (!parsedRequest.success) {
    return authErrorResponse(400, {
      message: firstSchemaError(parsedRequest.error),
    });
  }

  const loginRequest: LoginInitiateRequest = parsedRequest.data;

  try {
    const { response, result } = await requestAuthService<
      LoginInitiateResponse,
      LoginInitiateRequest
    >(authServiceEndpoints.initiateLogin, {
      method: "POST",
      body: loginRequest,
    });

    if (!response.ok || !result.success || !result.data) {
      return authErrorResponse(response.status, result);
    }

    const parsedResponse = loginInitiateResponseSchema.safeParse(result.data);
    if (!parsedResponse.success) {
      return authErrorResponse(502, {
        message: "The authentication service returned an invalid response.",
      });
    }

    return NextResponse.json({ ...result, data: parsedResponse.data });
  } catch {
    return authServiceUnavailableResponse();
  }
}
