import {
  apiClient,
  APP_API_ENDPOINTS,
  ensureApiSuccess,
} from "@/api";
import type { BackendApiResponse } from "@/api/types";
import { userProvisionRequestSchema } from "../schemas";
import type { UserProvisionRequest } from "./types";

export const provisionAuthUser = async (request: UserProvisionRequest) => {
  const parsedRequest = userProvisionRequestSchema.parse(request);
  const result = await apiClient<BackendApiResponse<null>, UserProvisionRequest>(
    APP_API_ENDPOINTS.auth.internalProvision,
    {
      method: "POST",
      body: parsedRequest,
    },
  );

  ensureApiSuccess(result, "Unable to provision user authentication.");
  return result;
};