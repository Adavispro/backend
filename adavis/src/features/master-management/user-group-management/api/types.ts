import type { z } from "zod";
import type {
  createGroupFormSchema,
  createGroupRequestSchema,
  groupSchema,
  groupsSchema,
  updateGroupFormSchema,
  updateGroupRequestSchema,
} from "../schemas";

export type Group = z.infer<typeof groupSchema>;
export type Groups = z.infer<typeof groupsSchema>;
export type CreateGroupFormValues = z.infer<typeof createGroupFormSchema>;
export type CreateGroupRequest = z.infer<typeof createGroupRequestSchema>;
export type UpdateGroupFormValues = z.infer<typeof updateGroupFormSchema>;
export type UpdateGroupRequest = z.infer<typeof updateGroupRequestSchema>;
