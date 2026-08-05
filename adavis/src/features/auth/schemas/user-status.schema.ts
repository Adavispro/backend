import { z } from "zod";

export const userAccountActionSchema = z.enum([
  "activate",
  "block",
  "deactivate",
  "unblock",
]);
