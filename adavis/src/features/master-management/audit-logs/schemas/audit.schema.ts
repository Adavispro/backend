import { z } from "zod";

const jsonRecordSchema = z.record(z.string(), z.unknown());

export const auditLogSchema = z
  .object({
    _id: z.string().optional(),
    id: z.string().optional(),
    eventId: z.string().nullish(),
    userId: z.string().nullish(),
    username: z.string().nullish(),
    action: z.string(),
    entity: z.string().nullish(),
    entityId: z.string().nullish(),
    before: jsonRecordSchema.nullish(),
    after: jsonRecordSchema.nullish(),
    metadata: jsonRecordSchema.nullish(),
    ipAddress: z.string().nullish(),
    sessionId: z.string().nullish(),
    userAgent: z.string().nullish(),
    tenantId: z.string().nullish(),
    status: z.string().nullish(),
    timestamp: z.string().nullish(),
    createdAt: z.string().nullish(),
    updatedAt: z.string().nullish(),
  })
  .passthrough();

export const auditLogsArraySchema = z.array(auditLogSchema);

export const auditLogsPageSchema = z.object({
  content: auditLogsArraySchema,
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  pageNumber: z.number().int().nonnegative().optional(),
  pageSize: z.number().int().positive().optional(),
  first: z.boolean().optional(),
  last: z.boolean().optional(),
  hasNext: z.boolean().optional(),
  hasPrevious: z.boolean().optional(),
});

export const auditLogsResultSchema = z.union([
  auditLogsPageSchema,
  auditLogsArraySchema,
]);

export const auditListQuerySchema = z.object({
  page: z.coerce.number().int().min(0).default(0),
  size: z.coerce.number().int().min(1).max(100).default(20),
});

export const auditEntityQuerySchema = z.object({
  entity: z.string().trim().min(1, "Entity is required."),
  entityId: z.string().trim().min(1, "Entity ID is required."),
});

export const auditActionQuerySchema = auditListQuerySchema.extend({
  action: z.string().trim().min(1, "Action is required."),
  from: z.string().trim().optional(),
  to: z.string().trim().optional(),
});

export const auditTenantQuerySchema = z.object({
  from: z.string().trim().optional(),
  to: z.string().trim().optional(),
});

export const auditCountByActionQuerySchema = z.object({
  action: z.string().trim().min(1, "Action is required."),
  from: z.string().trim().optional(),
  to: z.string().trim().optional(),
});

export const auditCountSchema = z.union([
  z.number(),
  z.object({ count: z.number() }).passthrough(),
]);

export const createAuditLogRequestSchema = z
  .object({
    eventId: z.string().trim().min(1, "Event ID is required."),
    userId: z.string().trim().min(1, "User ID is required."),
    username: z.string().trim().min(1, "Username is required."),
    action: z.string().trim().min(1, "Action is required."),
    entity: z.string().trim().min(1, "Entity is required."),
    entityId: z.string().trim().min(1, "Entity ID is required."),
    before: jsonRecordSchema.optional(),
    after: jsonRecordSchema.optional(),
    metadata: jsonRecordSchema.optional(),
    ipAddress: z.string().trim().optional(),
    sessionId: z.string().trim().optional(),
    userAgent: z.string().trim().optional(),
    tenantId: z.string().trim().optional(),
    status: z.string().trim().min(1, "Status is required."),
    timestamp: z.string().trim().min(1, "Timestamp is required."),
  })
  .passthrough();
