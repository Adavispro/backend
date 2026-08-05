import type { z } from "zod";

export const firstSchemaError = (error: z.ZodError) =>
  error.issues[0]?.message ?? "Invalid request.";
