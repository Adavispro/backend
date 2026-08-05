import { cookies } from "next/headers";
import {
  AUTH_COOKIE_NAMES,
  authErrorResponse,
  authServiceUnavailableResponse,
  requestAuthService,
} from "@/features/auth/api/auth.server";
import { firstSchemaError } from "@/features/auth/schemas";
import type { ApiMethod, BackendApiResponse } from "@/api/types";
import type { z } from "zod";

export const readJsonBody = async (request: Request) => {
  try {
    return await request.json();
  } catch {
    return undefined;
  }
};

const withLegacyAuthV1Path = (path: string) => {
  if (!path.startsWith("/api/auth/v1/")) {
    return null;
  }

  return path.replace("/api/auth/v1/", "/api/v1/auth/");
};

export const proxyPasswordRequest = async <TData, TBody>(
  path: string,
  options: {
    method?: ApiMethod;
    body?: unknown;
    schema?: z.ZodType<TBody>;
    requiresAuth?: boolean;
    invalidMessage: string;
  },
) => {
  const {
    body,
    invalidMessage,
    method = "POST",
    requiresAuth = false,
    schema,
  } = options;
  let parsedBody: TBody | undefined;

  if (schema) {
    const parsedRequest = schema.safeParse(body);
    if (!parsedRequest.success) {
      return authErrorResponse(400, {
        message: firstSchemaError(parsedRequest.error),
      });
    }
    parsedBody = parsedRequest.data;
  } else if (body !== undefined) {
    parsedBody = body as TBody;
  } else if (method !== "GET") {
    return authErrorResponse(400, { message: invalidMessage });
  }

  let accessToken: string | undefined;
  if (requiresAuth) {
    const cookieStore = await cookies();
    accessToken = cookieStore.get(AUTH_COOKIE_NAMES.accessToken)?.value;

    if (!accessToken) {
      return authErrorResponse(401, { message: "Not authenticated." });
    }
  }

  try {
    let { response, result } = await requestAuthService<TData, TBody>(path, {
      method,
      body: parsedBody,
      accessToken,
    });

    if (response.status === 404) {
      const fallbackPath = withLegacyAuthV1Path(path);
      if (fallbackPath) {
        const fallbackResponse = await requestAuthService<TData, TBody>(
          fallbackPath,
          {
            method,
            body: parsedBody,
            accessToken,
          },
        );
        response = fallbackResponse.response;
        result = fallbackResponse.result;
      }
    }

    if (!response.ok || !result.success) {
      return authErrorResponse(response.status, result);
    }

    return Response.json(result satisfies BackendApiResponse<TData>);
  } catch {
    return authServiceUnavailableResponse();
  }
};
