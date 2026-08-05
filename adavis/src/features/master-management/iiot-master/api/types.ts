import type { z } from "zod";
import type {
  createCriticalParameterLimitSchema,
  createCriticalParameterSchema,
  createIiotAssetSchema,
  createProductMasterSchema,
  criticalParameterLimitSchema,
  criticalParametersSchema,
  criticalParameterSchema,
  iiotAssetsSchema,
  iiotAssetSchema,
  productMasterSchema,
  updateCriticalParameterLimitSchema,
  updateCriticalParameterSchema,
  updateIiotAssetSchema,
  updateProductMasterSchema,
} from "../schemas";

export type IiotMasterSection =
  | "equipments"
  | "critical-parameters"
  | "critical-parameter-limits"
  | "product-master";

export type IiotAsset = z.infer<typeof iiotAssetSchema>;
export type IiotAssets = z.infer<typeof iiotAssetsSchema>;
export type CriticalParameter = z.infer<typeof criticalParameterSchema>;
export type CriticalParameters = z.infer<typeof criticalParametersSchema>;
export type CriticalParameterLimit = z.infer<typeof criticalParameterLimitSchema>;
export type ProductMaster = z.infer<typeof productMasterSchema>;
export type CreateIiotAssetValues = z.infer<typeof createIiotAssetSchema>;
export type CreateCriticalParameterValues = z.infer<
  typeof createCriticalParameterSchema
>;
export type CreateCriticalParameterLimitValues = z.infer<
  typeof createCriticalParameterLimitSchema
>;
export type CreateProductMasterValues = z.infer<typeof createProductMasterSchema>;
export type UpdateIiotAssetValues = z.infer<typeof updateIiotAssetSchema>;
export type UpdateCriticalParameterValues = z.infer<
  typeof updateCriticalParameterSchema
>;
export type UpdateCriticalParameterLimitValues = z.infer<
  typeof updateCriticalParameterLimitSchema
>;
export type UpdateProductMasterValues = z.infer<typeof updateProductMasterSchema>;
export type IiotMasterRecord =
  | IiotAsset
  | CriticalParameter
  | CriticalParameterLimit
  | ProductMaster;
