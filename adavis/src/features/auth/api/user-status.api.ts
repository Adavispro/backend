import { apiClient, ApiError, APP_API_ENDPOINTS } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import type { UserAccountAction } from "./types";

export const changeUserAccountStatus = async (
  userId: string,
  action: UserAccountAction,
) => {
  const result = await apiClient<BackendApiResponse<null>>(
    APP_API_ENDPOINTS.auth.userAction(userId, action),
    { method: "PATCH" },
  );

  if (!result.success) {
    throw new ApiError({
      status: 400,
      message: result.message || "Unable to change user status.",
      details: result,
    });
  }

  return result;
};
