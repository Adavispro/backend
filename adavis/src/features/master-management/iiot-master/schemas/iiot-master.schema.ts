import { z } from "zod";

const stringFromReference = (value: unknown) => {
  if (typeof value === "string") return value;
  if (typeof value === "number") return String(value);
  if (!value || typeof value !== "object" || Array.isArray(value)) return "";

  const record = value as Record<string, unknown>;
  const keys = [
    "equipmentId",
    "equipmentCode",
    "equipmentName",
    "parameterId",
    "parameterCode",
    "parameterName",
    "parameterLimitId",
    "parameterLimitCode",
    "productId",
    "productCode",
    "productName",
    "tenantId",
    "plantId",
    "areaId",
    "roomId",
    "id",
    "code",
    "name",
  ];

  for (const key of keys) {
    const candidate = record[key];
    if (typeof candidate === "string" && candidate.trim()) return candidate;
    if (typeof candidate === "number") return String(candidate);
  }

  return "";
};

const refString = z.preprocess(stringFromReference, z.string());
const optionalRefString = z.preprocess(
  (value) => {
    const normalized = stringFromReference(value);
    return normalized || undefined;
  },
  z.string().optional(),
);
const optionalNumber = z.preprocess(
  (value) => {
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim()) return Number(value);
    return undefined;
  },
  z.number().optional(),
);
const optionalBoolean = z.preprocess(
  (value) => {
    if (typeof value === "boolean") return value;
    if (typeof value === "string") return value.toLowerCase() === "true";
    return undefined;
  },
  z.boolean().optional(),
);
const optionalTimestamp = z
  .union([z.string(), z.number(), z.date()])
  .optional()
  .nullish();

export const iiotAssetSchema = z
  .object({
    _id: optionalRefString,
    equipmentId: refString,
    equipmentCode: refString,
    equipmentName: z.string().default(""),
    tenantId: refString,
    plantId: refString,
    areaId: refString,
    roomId: refString,
    isActive: optionalBoolean.default(true),
    createdAt: optionalTimestamp,
    updatedAt: optionalTimestamp,
  })
  .passthrough();

export const iiotAssetsSchema = z.array(iiotAssetSchema);

export const criticalParameterSchema = z
  .object({
    _id: optionalRefString,
    parameterId: refString,
    equipmentId: refString,
    parameterCode: refString,
    parameterName: z.string().default(""),
    unitOfMeasure: z.string().default(""),
    parameterType: z.string().default("FLOAT"),
    tenantId: refString,
    plantId: refString,
    isActive: optionalBoolean.default(true),
    createdAt: optionalTimestamp,
    updatedAt: optionalTimestamp,
  })
  .passthrough();

export const criticalParametersSchema = z.array(criticalParameterSchema);

export const criticalParameterLimitSchema = z
  .object({
    _id: optionalRefString,
    parameterLimitId: refString,
    parameterLimitCode: optionalRefString,
    parameterId: refString,
    parameterType: optionalRefString,
    equipmentId: refString,
    tenantId: refString,
    plantId: refString,
    lowCriticalValue: optionalNumber,
    highCriticalValue: optionalNumber,
    alarmEnabled: optionalBoolean.default(false),
    booleanValue: optionalBoolean.nullish(),
    enumValue: z.string().nullish(),
    stringValue: z.string().nullish(),
    isActive: optionalBoolean.default(true),
    createdAt: optionalTimestamp,
    updatedAt: optionalTimestamp,
  })
  .passthrough();

export const criticalParameterLimitsSchema = z.array(
  criticalParameterLimitSchema,
);

export const productMasterSchema = z
  .object({
    _id: optionalRefString,
    productId: refString,
    productCode: refString,
    productName: z.string().default(""),
    tenantId: refString,
    plantId: refString,
    isActive: optionalBoolean.default(true),
    createdAt: optionalTimestamp,
    updatedAt: optionalTimestamp,
  })
  .passthrough();

export const productMastersSchema = z.array(productMasterSchema);

const requiredText = (label: string) =>
  z.string().trim().min(1, `${label} is required.`);

export const createIiotAssetSchema = z.object({
  equipmentId: requiredText("Equipment ID"),
  equipmentCode: requiredText("Equipment code"),
  equipmentName: requiredText("Equipment name"),
  tenantId: requiredText("Tenant"),
  plantId: requiredText("Plant"),
  areaId: requiredText("Area"),
  roomId: requiredText("Room"),
  isActive: z.boolean().default(true),
});

export const createCriticalParameterSchema = z.object({
  parameterId: z.string().trim().optional(),
  equipmentId: requiredText("Equipment"),
  parameterCode: requiredText("Parameter code"),
  parameterName: requiredText("Parameter name"),
  unitOfMeasure: requiredText("Unit of measure"),
  parameterType: requiredText("Parameter type"),
  tenantId: requiredText("Tenant"),
  plantId: requiredText("Plant"),
  isActive: z.boolean().default(true),
});

export const createCriticalParameterLimitSchema = z.object({
  parameterLimitId: z.string().trim().optional(),
  parameterLimitCode: requiredText("Parameter limit code"),
  parameterId: requiredText("Critical parameter"),
  parameterType: requiredText("Parameter type"),
  equipmentId: requiredText("Equipment"),
  tenantId: requiredText("Tenant"),
  plantId: requiredText("Plant"),
  lowCriticalValue: optionalNumber,
  highCriticalValue: optionalNumber,
  alarmEnabled: z.boolean().default(false),
  booleanValue: optionalBoolean,
  enumValue: z.string().trim().optional(),
  stringValue: z.string().trim().optional(),
  isActive: z.boolean().default(true),
});

export const createProductMasterSchema = z.object({
  productId: z.string().trim().optional(),
  productCode: requiredText("Product code"),
  productName: requiredText("Product name"),
  tenantId: requiredText("Tenant"),
  plantId: requiredText("Plant"),
  isActive: z.boolean().default(true),
});

export const updateIiotAssetSchema = z.object({
  equipmentCode: requiredText("Equipment code"),
  equipmentName: requiredText("Equipment name"),
  tenantId: requiredText("Tenant"),
  plantId: requiredText("Plant"),
  areaId: requiredText("Area"),
  roomId: requiredText("Room"),
  isActive: z.boolean().default(true),
});

export const updateCriticalParameterSchema = z.object({
  equipmentId: requiredText("Equipment"),
  parameterCode: requiredText("Parameter code"),
  parameterName: requiredText("Parameter name"),
  unitOfMeasure: requiredText("Unit of measure"),
  parameterType: requiredText("Parameter type"),
  tenantId: requiredText("Tenant"),
  plantId: requiredText("Plant"),
  isActive: z.boolean().default(true),
});

export const updateCriticalParameterLimitSchema = z.object({
  parameterId: requiredText("Critical parameter"),
  parameterType: requiredText("Parameter type"),
  parameterLimitCode: requiredText("Parameter limit code"),
  equipmentId: requiredText("Equipment"),
  tenantId: requiredText("Tenant"),
  plantId: requiredText("Plant"),
  lowCriticalValue: optionalNumber,
  highCriticalValue: optionalNumber,
  alarmEnabled: z.boolean().default(false),
  booleanValue: optionalBoolean,
  enumValue: z.string().trim().optional(),
  stringValue: z.string().trim().optional(),
  isActive: z.boolean().default(true),
});

export const updateProductMasterSchema = z.object({
  productCode: requiredText("Product code"),
  productName: requiredText("Product name"),
  tenantId: requiredText("Tenant"),
  plantId: requiredText("Plant"),
  isActive: z.boolean().default(true),
});
