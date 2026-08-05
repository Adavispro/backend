import { apiClient, ApiError } from "@/api";
import type { BackendApiResponse } from "@/api/types";
import {
  criticalParameterLimitSchema,
  criticalParameterSchema,
  iiotAssetSchema,
  productMasterSchema,
} from "../schemas";
import type {
  CreateCriticalParameterLimitValues,
  CreateCriticalParameterValues,
  CreateIiotAssetValues,
  CreateProductMasterValues,
  CriticalParameter,
  CriticalParameterLimit,
  IiotAsset,
  IiotMasterSection,
  ProductMaster,
  UpdateCriticalParameterLimitValues,
  UpdateCriticalParameterValues,
  UpdateIiotAssetValues,
  UpdateProductMasterValues,
} from "./types";

const root = "/api/master-management/iiot";

const unwrapCollectionPayload = (value: unknown): unknown[] => {
  if (Array.isArray(value)) return value;
  if (!value || typeof value !== "object") return [];

  const record = value as Record<string, unknown>;
  const candidates = [
    record.data,
    record.items,
    record.content,
    record.records,
    record.results,
    record.payload,
    record.criticalParameterLimits,
    record.parameterLimits,
    record.equipmentMasters,
    record.criticalParameters,
    record.productMasters,
  ];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list : [];
};

const textId = (value: unknown, fallback = ""): string => {
  if (typeof value === "string" && value.trim()) return value;
  if (typeof value === "number") return String(value);
  if (!value || typeof value !== "object" || Array.isArray(value)) return fallback;

  const record = value as Record<string, unknown>;
  const nested =
    record.id ??
    record._id ??
    record.code ??
    record.name ??
    record.equipmentId ??
    record.parameterId ??
    record.parameterLimitId ??
    record.productId ??
    record.tenantId ??
    record.plantId ??
    record.areaId ??
    record.roomId;
  return textId(nested, fallback);
};

const scopedId = (equipmentId: unknown, localId: unknown, fallback = "") => {
  const equipment = textId(equipmentId);
  const local = textId(localId);
  if (equipment && local) return `${equipment}::${local}`;
  return local || fallback;
};

const normalizeIiotAsset = (value: unknown) => {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;
  const row = value as Record<string, unknown>;
  return {
    ...row,
    equipmentId: textId(row.equipmentId, textId(row.equipmentCode)),
    equipmentCode: textId(row.equipmentCode, textId(row.equipmentId)),
    tenantId: textId(row.tenantId),
    plantId: textId(row.plantId),
    areaId: textId(row.areaId),
    roomId: textId(row.roomId, textId(row.roomNo)),
  };
};

const normalizeCriticalParameter = (value: unknown) => {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;
  const row = value as Record<string, unknown>;
  const fallbackParameterId = scopedId(row.equipmentId, row.parameterCode);
  return {
    ...row,
    parameterId: textId(row.parameterId, fallbackParameterId),
    parameterCode: textId(row.parameterCode, textId(row.parameterId)),
    equipmentId: textId(row.equipmentId),
    tenantId: textId(row.tenantId),
    plantId: textId(row.plantId),
  };
};

const normalizeCriticalParameterLimit = (value: unknown) => {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;
  const row = value as Record<string, unknown>;
  const fallbackLimitId = scopedId(
    row.equipmentId,
    textId(row.parameterLimitCode, textId(row.parameterId)),
  );
  return {
    ...row,
    parameterLimitId: textId(
      row.parameterLimitId,
      fallbackLimitId,
    ),
    parameterLimitCode: textId(row.parameterLimitCode, textId(row.parameterLimitId)),
    parameterId: textId(row.parameterId),
    equipmentId: textId(row.equipmentId),
    tenantId: textId(row.tenantId),
    plantId: textId(row.plantId),
  };
};

const normalizeProductMaster = (value: unknown) => {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;
  const row = value as Record<string, unknown>;
  return {
    ...row,
    productId: textId(row.productId, textId(row.productCode)),
    productCode: textId(row.productCode, textId(row.productId)),
    tenantId: textId(row.tenantId),
    plantId: textId(row.plantId),
  };
};

const parseListLenient = <T>(
  list: unknown,
  normalizer: (value: unknown) => unknown,
  schema: { safeParse: (input: unknown) => { success: true; data: T } | { success: false } },
): T[] => {
  const normalizedRows = unwrapCollectionPayload(list).map(normalizer);
  const parsedRows: T[] = [];

  normalizedRows.forEach((row) => {
    const parsed = schema.safeParse(row);
    if (parsed.success) {
      parsedRows.push(parsed.data);
    }
  });

  return parsedRows;
};

const toNumber = (value: unknown): number | undefined => {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
};

const toBoolean = (value: unknown): boolean | undefined => {
  if (typeof value === "boolean") return value;
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (normalized === "true") return true;
    if (normalized === "false") return false;
  }
  return undefined;
};

const parseCriticalParameterLimitsRobust = (value: unknown) => {
  const normalizedRows = unwrapCollectionPayload(value).map((row) =>
    normalizeCriticalParameterLimit(row),
  );

  const strictRows = normalizedRows
    .map((row) => criticalParameterLimitSchema.safeParse(row))
    .filter((parsed): parsed is { success: true; data: CriticalParameterLimit } =>
      parsed.success,
    )
    .map((parsed) => parsed.data);

  if (strictRows.length > 0 || normalizedRows.length === 0) {
    return strictRows;
  }

  return normalizedRows
    .map((raw) => {
      const row =
        raw && typeof raw === "object" && !Array.isArray(raw)
          ? (raw as Record<string, unknown>)
          : {};

      return {
        ...row,
        parameterLimitId: textId(
          row.parameterLimitId,
          textId(row.parameterLimitCode, textId(row.parameterId)),
        ),
        parameterLimitCode: textId(
          row.parameterLimitCode,
          textId(row.parameterLimitId),
        ),
        parameterId: textId(row.parameterId),
        parameterType: textId(row.parameterType),
        equipmentId: textId(row.equipmentId),
        tenantId: textId(row.tenantId, "TNT-0001"),
        plantId: textId(row.plantId, "PLNT-0001"),
        lowCriticalValue: toNumber(row.lowCriticalValue),
        highCriticalValue: toNumber(row.highCriticalValue),
        alarmEnabled: toBoolean(row.alarmEnabled) ?? false,
        booleanValue: toBoolean(row.booleanValue),
        enumValue:
          row.enumValue === null || row.enumValue === undefined
            ? undefined
            : String(row.enumValue),
        stringValue:
          row.stringValue === null || row.stringValue === undefined
            ? undefined
            : String(row.stringValue),
        isActive: toBoolean(row.isActive) ?? true,
      };
    })
    .map((row) => criticalParameterLimitSchema.safeParse(row))
    .filter((parsed): parsed is { success: true; data: CriticalParameterLimit } =>
      parsed.success,
    )
    .map((parsed) => parsed.data);
};

const dataOrThrow = <T>(result: BackendApiResponse<T>, fallback: string) => {
  if (!result.success || result.data === null || result.data === undefined) {
    throw new ApiError({
      status: 400,
      message: result.message || fallback,
      details: result,
    });
  }

  return result.data;
};

export async function getIiotAssets(signal?: AbortSignal) {
  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/equipment-master`,
    { signal },
  );
  return parseListLenient(
    dataOrThrow(result, "Unable to load equipment masters."),
    normalizeIiotAsset,
    iiotAssetSchema,
  );
}

export async function getIiotAsset(equipmentId: string, signal?: AbortSignal) {
  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/equipment-master/${encodeURIComponent(equipmentId)}`,
    { signal },
  );
  return iiotAssetSchema.parse(
    dataOrThrow(result, "Unable to load equipment master."),
  );
}

export async function getCriticalParameters(signal?: AbortSignal) {
  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/critical-parameters`,
    { signal },
  );
  return parseListLenient(
    dataOrThrow(result, "Unable to load critical parameters."),
    normalizeCriticalParameter,
    criticalParameterSchema,
  );
}

export async function getCriticalParameter(
  parameterId: string,
  signal?: AbortSignal,
) {
  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/critical-parameters/${encodeURIComponent(parameterId)}`,
    { signal },
  );
  return criticalParameterSchema.parse(
    dataOrThrow(result, "Unable to load critical parameter."),
  );
}

export async function getCriticalParameterLimits(signal?: AbortSignal) {
  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/critical-parameter-limits`,
    { signal },
  );
  return parseCriticalParameterLimitsRobust(
    dataOrThrow(result, "Unable to load critical parameter limits."),
  );
}

export async function getCriticalParameterLimit(
  parameterLimitId: string,
  signal?: AbortSignal,
) {
  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/critical-parameter-limits/${encodeURIComponent(parameterLimitId)}`,
    { signal },
  );
  return criticalParameterLimitSchema.parse(
    dataOrThrow(result, "Unable to load critical parameter limit."),
  );
}

export async function getProductMasters(signal?: AbortSignal) {
  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/product-master`,
    { signal },
  );
  return parseListLenient(
    dataOrThrow(result, "Unable to load product master records."),
    normalizeProductMaster,
    productMasterSchema,
  );
}

export async function getProductMaster(productId: string, signal?: AbortSignal) {
  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/product-master/${encodeURIComponent(productId)}`,
    { signal },
  );
  return productMasterSchema.parse(
    dataOrThrow(result, "Unable to load product master record."),
  );
}

export async function createIiotAsset(request: CreateIiotAssetValues) {
  const result = await apiClient<
    BackendApiResponse<unknown>,
    CreateIiotAssetValues
  >(`${root}/equipment-master`, {
    method: "POST",
    body: request,
  });
  return iiotAssetSchema.parse(
    dataOrThrow(result, "Unable to create equipment master."),
  );
}

export async function updateIiotAsset(
  equipmentId: string,
  request: UpdateIiotAssetValues,
) {
  const result = await apiClient<
    BackendApiResponse<unknown>,
    UpdateIiotAssetValues
  >(`${root}/equipment-master/${encodeURIComponent(equipmentId)}`, {
    method: "PUT",
    body: request,
  });
  return iiotAssetSchema.parse(
    dataOrThrow(result, "Unable to update equipment master."),
  );
}

export async function createCriticalParameter(
  request: CreateCriticalParameterValues,
) {
  const result = await apiClient<
    BackendApiResponse<unknown>,
    CreateCriticalParameterValues
  >(`${root}/critical-parameters`, {
    method: "POST",
    body: request,
  });
  return criticalParameterSchema.parse(
    dataOrThrow(result, "Unable to create critical parameter."),
  );
}

export async function updateCriticalParameter(
  parameterId: string,
  request: UpdateCriticalParameterValues,
) {
  const result = await apiClient<
    BackendApiResponse<unknown>,
    UpdateCriticalParameterValues
  >(`${root}/critical-parameters/${encodeURIComponent(parameterId)}`, {
    method: "PUT",
    body: request,
  });
  return criticalParameterSchema.parse(
    dataOrThrow(result, "Unable to update critical parameter."),
  );
}

export async function createCriticalParameterLimit(
  request: CreateCriticalParameterLimitValues,
) {
  const result = await apiClient<
    BackendApiResponse<unknown>,
    CreateCriticalParameterLimitValues
  >(`${root}/critical-parameter-limits`, {
    method: "POST",
    body: request,
  });
  return criticalParameterLimitSchema.parse(
    dataOrThrow(result, "Unable to create critical parameter limit."),
  );
}

export async function updateCriticalParameterLimit(
  parameterLimitId: string,
  request: UpdateCriticalParameterLimitValues,
) {
  const result = await apiClient<
    BackendApiResponse<unknown>,
    UpdateCriticalParameterLimitValues
  >(`${root}/critical-parameter-limits/${encodeURIComponent(parameterLimitId)}`, {
    method: "PUT",
    body: request,
  });
  return criticalParameterLimitSchema.parse(
    dataOrThrow(result, "Unable to update critical parameter limit."),
  );
}

export async function createProductMaster(request: CreateProductMasterValues) {
  const result = await apiClient<
    BackendApiResponse<unknown>,
    CreateProductMasterValues
  >(`${root}/product-master`, {
    method: "POST",
    body: request,
  });
  return productMasterSchema.parse(
    dataOrThrow(result, "Unable to create product master record."),
  );
}

export async function updateProductMaster(
  productId: string,
  request: UpdateProductMasterValues,
) {
  const result = await apiClient<
    BackendApiResponse<unknown>,
    UpdateProductMasterValues
  >(`${root}/product-master/${encodeURIComponent(productId)}`, {
    method: "PUT",
    body: request,
  });
  return productMasterSchema.parse(
    dataOrThrow(result, "Unable to update product master record."),
  );
}

const sectionConfig = {
  equipments: {
    endpoint: "equipment-master",
    idField: "equipmentId",
    schema: iiotAssetSchema,
  },
  "critical-parameters": {
    endpoint: "critical-parameters",
    idField: "parameterId",
    schema: criticalParameterSchema,
  },
  "critical-parameter-limits": {
    endpoint: "critical-parameter-limits",
    idField: "parameterLimitId",
    schema: criticalParameterLimitSchema,
  },
  "product-master": {
    endpoint: "product-master",
    idField: "productId",
    schema: productMasterSchema,
  },
} as const;

type MutableSection = keyof typeof sectionConfig;
type MutableRecord =
  | IiotAsset
  | CriticalParameter
  | CriticalParameterLimit
  | ProductMaster;

export async function setIiotMasterRecordActive(
  section: MutableSection,
  record: MutableRecord,
  active: boolean,
) {
  const config = sectionConfig[section];
  const id = String(record[config.idField as keyof MutableRecord] ?? "");

  if (!id) {
    throw new ApiError({
      status: 400,
      message: "Record identifier is missing.",
    });
  }

  if (!active) {
    await apiClient<BackendApiResponse<unknown>>(
      `${root}/${config.endpoint}/${encodeURIComponent(id)}`,
      { method: "DELETE" },
    );
    return { ...record, isActive: false } as MutableRecord;
  }

  const result = await apiClient<BackendApiResponse<unknown>>(
    `${root}/${config.endpoint}/${encodeURIComponent(id)}/activate`,
    { method: "POST" },
  );
  return config.schema.parse(dataOrThrow(result, "Unable to activate record."));
}

export function isMutableIiotMasterSection(
  section: IiotMasterSection,
): section is MutableSection {
  return section in sectionConfig;
}
