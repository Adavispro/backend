import type { z } from "zod";
import type {
  auditActionQuerySchema,
  auditCountByActionQuerySchema,
  auditCountSchema,
  auditEntityQuerySchema,
  auditListQuerySchema,
  auditLogSchema,
  auditLogsArraySchema,
  auditLogsPageSchema,
  auditLogsResultSchema,
  auditTenantQuerySchema,
  createAuditLogRequestSchema,
} from "../schemas";

export type AuditLog = z.infer<typeof auditLogSchema>;
export type AuditLogsArray = z.infer<typeof auditLogsArraySchema>;
export type AuditLogsPage = z.infer<typeof auditLogsPageSchema>;
export type AuditLogsResult = z.infer<typeof auditLogsResultSchema>;
export type AuditListQuery = z.infer<typeof auditListQuerySchema>;
export type AuditEntityQuery = z.infer<typeof auditEntityQuerySchema>;
export type AuditActionQuery = z.infer<typeof auditActionQuerySchema>;
export type AuditTenantQuery = z.infer<typeof auditTenantQuerySchema>;
export type AuditCountByActionQuery = z.infer<
  typeof auditCountByActionQuerySchema
>;
export type AuditCount = z.infer<typeof auditCountSchema>;
export type CreateAuditLogRequest = z.infer<typeof createAuditLogRequestSchema>;
