import type { z } from "zod";
import type { assignmentSchema, assignmentsSchema, createAssignmentSchema } from "../schemas";

export type Assignment = z.infer<typeof assignmentSchema>;
export type Assignments = z.infer<typeof assignmentsSchema>;
export type CreateAssignmentValues = z.infer<typeof createAssignmentSchema>;
export type CreateAssignmentRequest = CreateAssignmentValues;
export type UpdateAssignmentRequest = CreateAssignmentRequest;
