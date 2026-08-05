import { z } from "zod";

export const userSchema = z.object({
  _id: z.string().optional(),
  id: z.string().optional(),
  userId: z.string(),
  userTrackId: z.string().nullish(),
  tenantId: z.string().nullish(),
  username: z.string().nullish(),
  email: z.string(),
  firstName: z.string().nullish(),
  lastName: z.string().nullish(),
  phoneNumber: z.string().nullish(),
  title: z.string().nullish(),
  userType: z.string().nullish(),
  lifecycleStatus: z.string().nullish(),
  empId: z.string().nullish(),
  departmentId: z.string().nullish(),
  designation: z.string().nullish(),
  isExternal: z.boolean().nullish(),
  isActive: z.boolean().default(true),
  isBlocked: z.boolean().default(false),
  isDeleted: z.boolean().default(false),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

export const usersPageSchema = z.object({
  content: z.array(userSchema),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  pageNumber: z.number().int().nonnegative(),
  pageSize: z.number().int().positive(),
  first: z.boolean(),
  last: z.boolean(),
  hasNext: z.boolean(),
  hasPrevious: z.boolean(),
});

const optionalQueryBoolean = z.preprocess(
  (value) => value === undefined ? undefined : value === true || value === "true",
  z.boolean().optional(),
);

export const usersListQuerySchema = z.object({
  page: z.coerce.number().int().min(0).default(0),
  size: z.coerce.number().int().min(1).max(100).default(20),
  isActive: optionalQueryBoolean,
  isBlocked: optionalQueryBoolean,
  lifecycleStatus: z.string().trim().min(1).optional(),
});
