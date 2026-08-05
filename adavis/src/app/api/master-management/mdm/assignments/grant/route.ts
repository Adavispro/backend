import { Buffer } from "node:buffer";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { z } from "zod";
import { serverApiClient } from "@/api/server-client";
import { SERVER_API_CONFIG } from "@/api/server-config";
import type { BackendApiResponse } from "@/api/types";
import { SELECTED_PLANT_HEADER } from "@/utils/plantSelection";
import { AUTH_COOKIE_NAMES } from "@/features/auth/api/auth.server";
import { assignmentSchema } from "@/features/master-management/assignment-management/schemas";

const assignmentTypeSchema = z.enum(["GROUP_SCOPE", "USER_OVERRIDE"]);
const scopeTypeSchema = z.enum(["PLANT", "RESOURCE"]);

const optionalString = z.preprocess(
  (value) => {
    if (typeof value !== "string") return value;
    const trimmed = value.trim();
    return trimmed.length ? trimmed : undefined;
  },
  z.string().trim().min(1).optional(),
);

const assignmentGrantSchema = z
  .object({
    assignmentType: assignmentTypeSchema,
    tenantId: z.string().trim().min(1, "Tenant is required."),
    userId: optionalString,
    groupId: optionalString,
    scopeType: scopeTypeSchema,
    plantId: optionalString,
    resourceId: optionalString,
    reason: optionalString,
    assignedBy: optionalString,
  })
  .superRefine((value, context) => {
    if (!value.groupId) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: "User group is required.",
        path: ["groupId"],
      });
    }

    if (!value.userId) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: "User is required.",
        path: ["userId"],
      });
    }

    if (value.scopeType === "PLANT" && !value.plantId) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Plant is required.",
        path: ["plantId"],
      });
    }

    if (value.scopeType === "PLANT" && value.resourceId) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Resource ID must be empty for PLANT scope.",
        path: ["resourceId"],
      });
    }

    if (value.scopeType === "RESOURCE" && !value.resourceId) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Resource is required.",
        path: ["resourceId"],
      });
    }

    if (value.scopeType === "RESOURCE" && value.plantId) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Plant must be empty for RESOURCE scope.",
        path: ["plantId"],
      });
    }
  });

const errorResponse = (status: number, message: string, errorCode: string, details?: unknown) =>
  NextResponse.json(
    {
      success: false,
      message,
      errorCode,
      ...(details ? { details } : {}),
      timestamp: new Date().toISOString(),
    } satisfies BackendApiResponse<never>,
    { status },
  );

const readTokenSubject = (token: string) => {
  try {
    const payload = token.split(".")[1];
    if (!payload) return undefined;

    const decoded = JSON.parse(
      Buffer.from(payload, "base64url").toString("utf8"),
    ) as { sub?: unknown };

    return typeof decoded.sub === "string" && decoded.sub.trim()
      ? decoded.sub.trim()
      : undefined;
  } catch {
    return undefined;
  }
};

const normalizeAssignment = (value: unknown) => {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;

  const record = value as Record<string, unknown>;
  const explicitAssignmentType = typeof record.assignmentType === "string" ? record.assignmentType : "";
  const groupId = typeof record.groupId === "string" ? record.groupId : "";
  const userId = typeof record.userId === "string" ? record.userId : "";
  const assignmentType = explicitAssignmentType || (groupId ? "GROUP_SCOPE" : userId ? "USER_OVERRIDE" : "GROUP_SCOPE");

  const explicitScopeType = typeof record.scopeType === "string" ? record.scopeType : "";
  const legacyResourceType = typeof record.resourceType === "string" ? record.resourceType : "";
  const scopeType = explicitScopeType || (legacyResourceType === "ASSET" ? "RESOURCE" : "PLANT");

  const plantId = typeof record.plantId === "string" ? record.plantId : "";
  const resourceId = typeof record.resourceId === "string" ? record.resourceId : "";

  return {
    ...record,
    assignmentType,
    scopeType,
    plantId: scopeType === "PLANT" ? plantId || resourceId || undefined : undefined,
    resourceId: scopeType === "RESOURCE" ? resourceId || undefined : undefined,
    resourceType: scopeType === "RESOURCE" ? "ASSET" : "PLANT",
  };
};

export async function POST(request: Request) {
  const accessToken = (await cookies()).get(
    AUTH_COOKIE_NAMES.accessToken,
  )?.value;
  if (!accessToken) {
    return errorResponse(401, "Not authenticated.", "UNAUTHORIZED");
  }

  const assignedBy = readTokenSubject(accessToken);
  if (!assignedBy) {
    return errorResponse(401, "Invalid access token.", "INVALID_TOKEN");
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return errorResponse(400, "Invalid request payload.", "INVALID_PAYLOAD");
  }

  const parsed = assignmentGrantSchema.safeParse(body);
  if (!parsed.success) {
    return errorResponse(
      400,
      "Assignment payload validation failed.",
      "VALIDATION_FAILED",
      parsed.error.flatten(),
    );
  }

  const selectedPlantId = request.headers.get(SELECTED_PLANT_HEADER)?.trim();
  const requestUrl = new URL(request.url);
  const upstreamPath = `/api/v1/mdm/assignments/grant${requestUrl.search}`;
  const payload = {
    ...parsed.data,
    reason: parsed.data.reason ?? "Not provided",
    assignedBy,
  };

  try {
    const { response, result } = await serverApiClient<unknown, typeof payload>(
      SERVER_API_CONFIG.gatewayUrl,
      upstreamPath,
      {
        method: "POST",
        body: payload,
        headers: {
          Authorization: `Bearer ${accessToken}`,
          ...(selectedPlantId ? { [SELECTED_PLANT_HEADER]: selectedPlantId } : {}),
        },
      },
    );

    if (response.ok && result.success && result.data != null) {
      const parsedAssignment = assignmentSchema.safeParse(
        normalizeAssignment(result.data),
      );

      if (!parsedAssignment.success) {
        return errorResponse(
          502,
          "The assignment service returned an invalid response.",
          "INVALID_UPSTREAM_RESPONSE",
          parsedAssignment.error.flatten(),
        );
      }

      return NextResponse.json(
        {
          ...result,
          data: parsedAssignment.data,
        } satisfies BackendApiResponse<unknown>,
        { status: response.status },
      );
    }

    return NextResponse.json(result, { status: response.status });
  } catch {
    return errorResponse(
      503,
      "The Master Data Management service is unavailable.",
      "MDM_SERVICE_UNAVAILABLE",
    );
  }
}
