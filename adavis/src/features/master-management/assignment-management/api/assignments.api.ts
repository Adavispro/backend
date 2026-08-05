import { apiClient, ApiError, withQuery } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import { assignmentSchema, assignmentsSchema } from "../schemas";
import type { Assignment, CreateAssignmentRequest, CreateAssignmentValues, UpdateAssignmentRequest } from "./types";

const root = "/api/master-management/mdm/assignments";

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

const normalizeAssignmentData = (value: unknown) =>
  Array.isArray(value) ? value.map(normalizeAssignment) : normalizeAssignment(value);

const dataOrThrow = <T>(result: BackendApiResponse<T>, message: string) => {
  if (!result.success || result.data == null) throw new ApiError({ status: 400, message: result.message || message, details: result });
  return result.data;
};

const toRequest = (values: CreateAssignmentValues): CreateAssignmentRequest => {
  const request: CreateAssignmentRequest = {
    assignmentType: values.assignmentType,
    tenantId: values.tenantId,
    scopeType: values.scopeType,
    reason: values.reason?.trim() || "Not provided",
  };

  request.groupId = values.groupId;
  request.userId = values.userId;
  if (values.scopeType === "PLANT") request.plantId = values.plantId;
  if (values.scopeType === "RESOURCE") request.resourceId = values.resourceId;

  return request;
};

const toGrantFallbackRequest = (
  assignment: Assignment,
  values: CreateAssignmentValues,
): CreateAssignmentRequest => {
  const request = toRequest(values);

  // Preserve immutable principal identifiers from the existing record for
  // legacy backend grant contracts that still require both IDs.
  if (values.assignmentType === "GROUP_SCOPE" && !request.userId && assignment.userId) {
    request.userId = assignment.userId;
  }
  if (values.assignmentType === "USER_OVERRIDE" && !request.groupId && assignment.groupId) {
    request.groupId = assignment.groupId;
  }

  return request;
};

const requestFromAssignment = (assignment: Assignment): CreateAssignmentRequest => {
  const assignmentType = assignment.assignmentType === "USER_OVERRIDE" ? "USER_OVERRIDE" : "GROUP_SCOPE";
  const scopeType = assignment.scopeType === "RESOURCE" ? "RESOURCE" : "PLANT";

  const request: CreateAssignmentRequest = {
    assignmentType,
    tenantId: assignment.tenantId,
    scopeType,
    reason: assignment.reason ?? "",
    groupId: assignment.groupId ?? "",
    userId: assignment.userId ?? "",
  };

  if (scopeType === "PLANT") request.plantId = assignment.plantId ?? assignment.resourceId ?? "";
  if (scopeType === "RESOURCE") request.resourceId = assignment.resourceId ?? "";

  return request;
};

const normalizeComparableRequest = (request: CreateAssignmentRequest): CreateAssignmentRequest => {
  const assignmentType = request.assignmentType === "USER_OVERRIDE" ? "USER_OVERRIDE" : "GROUP_SCOPE";
  const scopeType = request.scopeType === "RESOURCE" ? "RESOURCE" : "PLANT";

  const normalize = (value?: string | null) => {
    const trimmed = typeof value === "string" ? value.trim() : "";
    if (trimmed.toLowerCase() === "not provided") return "";
    return trimmed || "";
  };

  const normalized: CreateAssignmentRequest = {
    assignmentType,
    tenantId: normalize(request.tenantId),
    scopeType,
    reason: normalize(request.reason),
    groupId: normalize(request.groupId),
    userId: normalize(request.userId),
  };

  if (scopeType === "PLANT") normalized.plantId = normalize(request.plantId);
  if (scopeType === "RESOURCE") normalized.resourceId = normalize(request.resourceId);

  return normalized;
};

const isUnchangedRequest = (assignment: Assignment, request: CreateAssignmentRequest) => {
  const current = normalizeComparableRequest(requestFromAssignment(assignment));
  const incoming = normalizeComparableRequest(request);
  return JSON.stringify(current) === JSON.stringify(incoming);
};

export const getAssignments = async (isActive: boolean, signal?: AbortSignal) => {
  const result = await apiClient<BackendApiResponse<unknown>>(withQuery(root, { isActive }), { signal });
  const data = dataOrThrow(result, "Unable to load assignments.");
  return assignmentsSchema.parse(normalizeAssignmentData(data));
};

export const getAllAssignments = async (signal?: AbortSignal) => {
  const [active, inactive] = await Promise.all([getAssignments(true, signal), getAssignments(false, signal)]);
  return [...active, ...inactive];
};

export const createAssignment = async (values: CreateAssignmentValues) => {
  const request = toRequest(values);
  const endpoint = `${root}/grant`;
  const result = await apiClient<BackendApiResponse<unknown>, CreateAssignmentRequest>(endpoint, { method: "POST", body: request });
  const data = dataOrThrow(result, "Unable to create assignment.");
  return assignmentSchema.parse(normalizeAssignmentData(data));
};

export const updateAssignment = async (assignment: Assignment, values: CreateAssignmentValues) => {
  const request = toRequest(values);
  if (isUnchangedRequest(assignment, request)) return assignment;

  const updateEndpoint = `${root}/${encodeURIComponent(assignment.assignmentId)}`;
  const grantEndpoint = `${root}/grant`;
  try {
    const result = await apiClient<BackendApiResponse<unknown>, UpdateAssignmentRequest>(
      updateEndpoint,
      { method: "PUT", body: request },
    );
    const data = dataOrThrow(result, "Unable to update assignment.");
    const updated = assignmentSchema.parse(normalizeAssignmentData(data));

    if (!assignment.isActive && updated.isActive) {
      await deactivateAssignment(updated.assignmentId);
      return { ...updated, isActive: false };
    }

    return updated;
  } catch (error) {
    if (
      !(error instanceof ApiError) ||
      (error.status !== 400 && error.status !== 404 && error.status !== 405 && error.status < 500)
    ) {
      throw error;
    }
  }

  const grantRequest = toGrantFallbackRequest(assignment, values);

  try {
    await deactivateAssignment(assignment.assignmentId);
    const result = await apiClient<BackendApiResponse<unknown>, UpdateAssignmentRequest>(
      grantEndpoint,
      { method: "POST", body: grantRequest },
    );
    const data = dataOrThrow(result, "Unable to update assignment.");
    const updated = assignmentSchema.parse(normalizeAssignmentData(data));

    if (!assignment.isActive) {
      await deactivateAssignment(updated.assignmentId);
      return { ...updated, isActive: false };
    }

    return updated;
  } catch (error) {
    if (assignment.isActive) {
      await reactivateAssignment(assignment.assignmentId).catch(() => undefined);
    }

    if (error instanceof ApiError && error.status >= 500) {
      throw new ApiError({
        status: error.status,
        message: "Assignment update is currently unavailable. Please contact backend support.",
        details: error.details,
      });
    }

    throw error;
  }
};

export const deactivateAssignment = async (assignmentId: string) => {
  const result = await apiClient<BackendApiResponse<null>>(`${root}/${encodeURIComponent(assignmentId)}`, { method: "DELETE" });
  if (!result.success) throw new ApiError({ status: 400, message: result.message || "Unable to deactivate assignment.", details: result });
};

export const reactivateAssignment = async (assignmentId: string): Promise<Assignment> => {
  const result = await apiClient<BackendApiResponse<unknown>>(`${root}/${encodeURIComponent(assignmentId)}/activate`, { method: "POST" });
  const data = dataOrThrow(result, "Unable to reactivate assignment.");
  return assignmentSchema.parse(normalizeAssignmentData(data));
};
