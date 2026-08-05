import type { z } from "zod";
import type {
  userSchema,
  usersListQuerySchema,
  usersPageSchema,
} from "../schemas";
import type {
  createUserFormSchema,
  createUserRequestSchema,
  updateUserFormSchema,
  updateUserRequestSchema,
} from "../schemas";

export type User = z.infer<typeof userSchema>;
export type UsersPage = z.infer<typeof usersPageSchema>;
export type UsersListQuery = z.infer<typeof usersListQuerySchema>;
export type CreateUserFormValues = z.infer<typeof createUserFormSchema>;
export type CreateUserRequest = z.infer<typeof createUserRequestSchema>;
export type UpdateUserFormValues = z.infer<typeof updateUserFormSchema>;
export type UpdateUserRequest = z.infer<typeof updateUserRequestSchema>;
