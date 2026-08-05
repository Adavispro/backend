import { z } from "zod";
import { ApiError } from "./client";
import type { BackendApiResponse } from "./types";

export const ensureApiSuccess = (
  result: BackendApiResponse<unknown>,
  fallbackMessage: string,
) => {
  if (!result.success) {
    throw new ApiError({
      status: 400,
      message: result.message || fallbackMessage,
      details: result,
    });
  }
};

export const requireApiData = <T>(
  result: BackendApiResponse<T>,
  fallbackMessage: string,
) => {
  ensureApiSuccess(result, fallbackMessage);

  if (result.data === null || result.data === undefined) {
    throw new ApiError({
      status: 502,
      message: fallbackMessage,
      details: result,
    });
  }

  return result.data;
};

export const parseApiData = <TSchema extends z.ZodTypeAny>(
  result: BackendApiResponse<unknown>,
  schema: TSchema,
  failureMessage: string,
  invalidResponseMessage = "The API returned an invalid response.",
): z.infer<TSchema> => {
  const data = requireApiData(result, failureMessage);
  const parsed = schema.safeParse(data);

  if (!parsed.success) {
    throw new ApiError({
      status: 502,
      message: invalidResponseMessage,
      details: parsed.error.flatten(),
    });
  }

  return parsed.data;
};
