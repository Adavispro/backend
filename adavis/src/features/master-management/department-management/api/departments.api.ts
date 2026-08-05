import { apiClient, ApiError, APP_API_ENDPOINTS, withQuery } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import { departmentSchema, departmentsSchema } from "../schemas";
import type {
  CreateDepartmentRequest,
  Department,
  Departments,
  UpdateDepartmentRequest,
} from "./types";

export const getDepartments = async (isActive?: boolean, signal?: AbortSignal) => {
  const result = await apiClient<BackendApiResponse<Departments>>(
    withQuery(APP_API_ENDPOINTS.masterManagement.departments, { isActive }),
    { signal },
  );

  if (!result.success || !result.data) {
    throw new ApiError({
      status: 400,
      message: result.message || "Unable to load departments.",
      details: result,
    });
  }

  const parsedDepartments = departmentsSchema.safeParse(result.data);
  if (!parsedDepartments.success) {
    throw new ApiError({
      status: 502,
      message: "The department service returned an invalid response.",
      details: parsedDepartments.error.flatten(),
    });
  }

  return parsedDepartments.data;
};

export const getAllDepartments = async (signal?: AbortSignal) => {
  const [active, inactive] = await Promise.all([
    getDepartments(true, signal),
    getDepartments(false, signal),
  ]);
  return [...active, ...inactive];
};

export const createDepartment = async (request: CreateDepartmentRequest) => {
  const result = await apiClient<
    BackendApiResponse<Department>,
    CreateDepartmentRequest
  >(APP_API_ENDPOINTS.masterManagement.departments, {
    method: "POST",
    body: request,
  });

  if (!result.success || !result.data) {
    throw new ApiError({
      status: 400,
      message: result.message || "Unable to create department.",
      details: result,
    });
  }

  const parsedDepartment = departmentSchema.safeParse(result.data);
  if (!parsedDepartment.success) {
    throw new ApiError({
      status: 502,
      message: "The department service returned an invalid response.",
      details: parsedDepartment.error.flatten(),
    });
  }

  return parsedDepartment.data;
};

export const updateDepartment = async (
  departmentId: string,
  request: UpdateDepartmentRequest,
) => {
  const result = await apiClient<
    BackendApiResponse<Department>,
    UpdateDepartmentRequest
  >(APP_API_ENDPOINTS.masterManagement.departmentDetail(departmentId), {
    method: "PUT",
    body: request,
  });

  if (!result.success || !result.data) {
    throw new ApiError({
      status: 400,
      message: result.message || "Unable to update department.",
      details: result,
    });
  }

  const parsedDepartment = departmentSchema.safeParse(result.data);
  if (!parsedDepartment.success) {
    throw new ApiError({
      status: 502,
      message: "The department service returned an invalid response.",
      details: parsedDepartment.error.flatten(),
    });
  }

  return parsedDepartment.data;
};

export const setDepartmentActive = async (
  department: Department,
  active: boolean,
) => {
  const result = await apiClient<BackendApiResponse<Department | null>>(
    `/api/master-management/mdm/departments/${encodeURIComponent(department.departmentId)}/${active ? "activate" : "deactivate"}`,
    { method: "POST" },
  );

  if (!result.success) {
    throw new ApiError({
      status: 400,
      message: result.message || `Unable to ${active ? "activate" : "deactivate"} department.`,
      details: result,
    });
  }

  if (!active) return { ...department, isActive: false };
  return departmentSchema.parse(result.data);
};
