import { updateRoleRequestSchema } from "../schemas";
import type {
  CreateRoleFormValues,
  Role,
  UpdateRoleRequest,
} from "./types";

export const buildUpdateRoleRequest = (
  role: Role,
  values: CreateRoleFormValues,
): UpdateRoleRequest => {
  const name = values.name.trim();
  const description = values.description?.trim();

  return updateRoleRequestSchema.parse({
    tenantId: values.tenantId.trim(),
    roleCode: values.roleCode.trim(),
    name,
    roleName: name,
    description: description || undefined,
    isActive: role.isActive,
    parentRoleId: role.parentRoleId ?? null,
    level: role.level ?? 0,
  });
};
