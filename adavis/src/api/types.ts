export type ApiMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export interface ApiRequestOptions<TBody = unknown>
  extends Omit<RequestInit, "body" | "method"> {
  method?: ApiMethod;
  body?: TBody;
  baseUrl?: string;
  retryOnUnauthorized?: boolean;
  timeoutMs?: number;
}

export interface ApiErrorResponse {
  status: number;
  message: string;
  details?: unknown;
}

export interface BackendApiResponse<TData> {
  success: boolean;
  message: string;
  errorCode?: string | null;
  data?: TData | null;
  timestamp: string;
}
