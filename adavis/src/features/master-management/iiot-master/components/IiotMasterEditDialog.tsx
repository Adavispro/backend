"use client";

import { useMemo, useState } from "react";
import { CaretDown } from "@phosphor-icons/react";
import { Button, Dialog, TextField } from "@/components/ui";
import { usePlantTopology } from "../../plant-topology/hooks/usePlantTopology";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import type {
  CriticalParameter,
  CriticalParameterLimit,
  IiotAsset,
  IiotMasterRecord,
  IiotMasterSection,
  ProductMaster,
  UpdateCriticalParameterLimitValues,
  UpdateCriticalParameterValues,
  UpdateIiotAssetValues,
  UpdateProductMasterValues,
} from "../api";
import {
  updateCriticalParameterLimitSchema,
  updateCriticalParameterSchema,
  updateIiotAssetSchema,
  updateProductMasterSchema,
} from "../schemas";

type EditValues = Record<string, string | boolean>;

interface EditField {
  id: string;
  label: string;
  placeholder?: string;
  kind?: "text" | "number" | "checkbox" | "select";
  options?: Array<{ value: string; label: string }>;
  disabled?: boolean;
}

interface IiotMasterEditDialogProps {
  isOpen: boolean;
  section: IiotMasterSection;
  record: IiotMasterRecord | null;
  equipments: IiotAsset[];
  criticalParameters: CriticalParameter[];
  isSaving?: boolean;
  onClose: () => void;
  onSave: (
    values:
      | UpdateIiotAssetValues
      | UpdateCriticalParameterValues
      | UpdateCriticalParameterLimitValues
      | UpdateProductMasterValues,
  ) => Promise<void>;
}

const sectionTitles: Record<IiotMasterSection, string> = {
  equipments: "Edit Equipment",
  "critical-parameters": "Edit Critical Parameter",
  "critical-parameter-limits": "Edit Critical Parameter Limit",
  "product-master": "Edit Product Master",
};

const option = (value: string, label = value) => ({ value, label });
const emptyOption = (label: string) => ({ value: "", label });
const parameterTypeOptions: Array<{ value: string; label: string }> = [
  { value: "", label: "Select Parameter Type" },
  { value: "INT", label: "INT" },
  { value: "FLOAT", label: "FLOAT" },
  { value: "BOOLEAN", label: "BOOLEAN" },
  { value: "ENUM", label: "ENUM" },
  { value: "RANGE", label: "RANGE" },
  { value: "STRING", label: "STRING" },
];

const booleanOptions: Array<{ value: string; label: string }> = [
  { value: "", label: "Select Boolean Value" },
  { value: "true", label: "True" },
  { value: "false", label: "False" },
];

const initialValues = (
  section: IiotMasterSection,
  record: IiotMasterRecord | null,
  criticalParameters: CriticalParameter[],
): EditValues => {
  if (section === "equipments" && record && "equipmentId" in record) {
    const equipment = record as IiotAsset;
    return {
      equipmentCode: equipment.equipmentCode,
      equipmentName: equipment.equipmentName,
      tenantId: equipment.tenantId,
      plantId: equipment.plantId,
      areaId: equipment.areaId,
      roomId: equipment.roomId,
      isActive: Boolean(equipment.isActive),
    };
  }

  if (section === "critical-parameters" && record && "parameterId" in record) {
    const parameter = record as CriticalParameter;
    return {
      equipmentId: parameter.equipmentId,
      parameterCode: parameter.parameterCode,
      parameterName: parameter.parameterName,
      unitOfMeasure: parameter.unitOfMeasure,
      parameterType: parameter.parameterType,
      tenantId: parameter.tenantId,
      plantId: parameter.plantId,
      isActive: Boolean(parameter.isActive),
    };
  }

  if (
    section === "critical-parameter-limits" &&
    record &&
    "parameterLimitId" in record
  ) {
    const limit = record as CriticalParameterLimit;
    const parameter = criticalParameters.find(
      (item) => item.parameterId === limit.parameterId,
    );
    const limitRecord = limit as Record<string, unknown>;
    const resolvedType = String(
      limitRecord.parameterType ?? parameter?.parameterType ?? "",
    ).toUpperCase();
    const booleanFromValue = limitRecord.booleanValue;
    const booleanFromRange =
      typeof limit.lowCriticalValue === "number" &&
      typeof limit.highCriticalValue === "number"
        ? limit.lowCriticalValue === 1 && limit.highCriticalValue === 1
        : undefined;
    return {
      parameterId: limit.parameterId,
      parameterType: resolvedType,
      parameterLimitCode: String(
        limitRecord.parameterLimitCode ?? limit.parameterLimitId,
      ),
      equipmentId: limit.equipmentId,
      tenantId: limit.tenantId,
      plantId: limit.plantId,
      lowCriticalValue: String(limit.lowCriticalValue ?? ""),
      highCriticalValue: String(limit.highCriticalValue ?? ""),
      alarmEnabled: Boolean(limitRecord.alarmEnabled ?? false),
      booleanValue:
        typeof booleanFromValue === "boolean"
          ? String(booleanFromValue)
          : typeof booleanFromRange === "boolean"
            ? String(booleanFromRange)
            : "",
      enumValue: String(limitRecord.enumValue ?? ""),
      stringValue: String(limitRecord.stringValue ?? ""),
      isActive: Boolean(limit.isActive),
    };
  }

  if (section === "product-master" && record && "productId" in record) {
    const product = record as ProductMaster;
    return {
      productCode: product.productCode,
      productName: product.productName,
      tenantId: product.tenantId,
      plantId: product.plantId,
      isActive: Boolean(product.isActive),
    };
  }

  return {};
};

export default function IiotMasterEditDialog({
  isOpen,
  section,
  record,
  equipments,
  criticalParameters,
  isSaving = false,
  onClose,
  onSave,
}: IiotMasterEditDialogProps) {
  const { tenants } = useTenants();
  const { data: topology } = usePlantTopology();
  const [values, setValues] = useState<EditValues>(() =>
    initialValues(section, record, criticalParameters),
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const tenantId = String(values.tenantId ?? "");
  const plantId = String(values.plantId ?? "");
  const areaId = String(values.areaId ?? "");

  const fields = useMemo<EditField[]>(() => {
    if (section === "equipments") {
      const tenantOptions = [
        emptyOption("Select Tenant"),
        ...tenants
          .filter((tenant) => tenant.isActive)
          .map((tenant) =>
            option(tenant.tenantId, `${tenant.companyName} (${tenant.tenantId})`),
          ),
      ];

      const plantOptions = [
        emptyOption("Select Plant"),
        ...topology.plants
          .filter(
            (plant) =>
              plant.isActive && (!tenantId || plant.tenantId === tenantId),
          )
          .map((plant) =>
            option(plant.plantId, `${plant.plantName} (${plant.plantId})`),
          ),
      ];

      const areaOptions = [
        emptyOption("Select Area"),
        ...topology.areas
          .filter(
            (area) =>
              area.isActive &&
              (!tenantId || area.tenantId === tenantId) &&
              (!plantId || area.plantId === plantId),
          )
          .map((area) => option(area.areaId, `${area.areaName} (${area.areaId})`)),
      ];

      const roomOptions = [
        emptyOption("Select Room"),
        ...topology.rooms
          .filter(
            (room) =>
              room.isActive &&
              (!tenantId || room.tenantId === tenantId) &&
              (!plantId || room.plantId === plantId) &&
              (!areaId || room.areaId === areaId),
          )
          .map((room) => option(room.roomId, `${room.roomName} (${room.roomId})`)),
      ];

      return [
        { id: "tenantId", label: "Tenant", kind: "select", options: tenantOptions },
        { id: "plantId", label: "Plant", kind: "select", options: plantOptions },
        { id: "areaId", label: "Area", kind: "select", options: areaOptions },
        { id: "roomId", label: "Room", kind: "select", options: roomOptions },
        { id: "equipmentCode", label: "Equipment Code" },
        { id: "equipmentName", label: "Equipment Name" },
        { id: "isActive", label: "Active", kind: "checkbox" },
      ];
    }

    if (section === "critical-parameters") {
      const equipmentOptions = [
        emptyOption("Select Equipment"),
        ...equipments
          .filter((equipment) => equipment.isActive)
          .map((equipment) =>
            option(
              equipment.equipmentId,
              `${equipment.equipmentName} (${equipment.equipmentCode})`,
            ),
          ),
      ];

      return [
        { id: "equipmentId", label: "Equipment", kind: "select", options: equipmentOptions },
        { id: "parameterCode", label: "Parameter Code" },
        { id: "parameterName", label: "Parameter Name" },
        { id: "unitOfMeasure", label: "Unit Of Measure" },
        { id: "parameterType", label: "Parameter Type", kind: "select", options: parameterTypeOptions },
        { id: "isActive", label: "Active", kind: "checkbox" },
      ];
    }

    if (section === "critical-parameter-limits") {
      const parameterOptions = [
        emptyOption("Select Critical Parameter"),
        ...criticalParameters
          .filter((parameter) => parameter.isActive)
          .map((parameter) =>
            option(
              parameter.parameterId,
              `${parameter.parameterName} (${parameter.parameterCode})`,
            ),
          ),
      ];
      const limitType = String(values.parameterType ?? "").toUpperCase();
      const baseFields: EditField[] = [
        { id: "parameterLimitCode", label: "Parameter Limit Code" },
        { id: "parameterId", label: "Critical Parameter", kind: "select", options: parameterOptions },
        { id: "parameterType", label: "Parameter Type", disabled: true },
        { id: "alarmEnabled", label: "Alarm Enabled", kind: "checkbox" },
      ];

      if (limitType === "INT" || limitType === "FLOAT" || limitType === "RANGE") {
        return [
          ...baseFields,
          { id: "lowCriticalValue", label: "Low Critical Value", kind: "number" },
          { id: "highCriticalValue", label: "High Critical Value", kind: "number" },
          { id: "isActive", label: "Active", kind: "checkbox" },
        ];
      }

      if (limitType === "BOOLEAN") {
        return [
          ...baseFields,
          { id: "booleanValue", label: "Boolean Value", kind: "select", options: booleanOptions },
          { id: "isActive", label: "Active", kind: "checkbox" },
        ];
      }

      if (limitType === "ENUM") {
        return [
          ...baseFields,
          { id: "enumValue", label: "Enum Value" },
          { id: "isActive", label: "Active", kind: "checkbox" },
        ];
      }

      if (limitType === "STRING") {
        return [
          ...baseFields,
          { id: "stringValue", label: "String Value" },
          { id: "isActive", label: "Active", kind: "checkbox" },
        ];
      }

      return [
        ...baseFields,
        { id: "isActive", label: "Active", kind: "checkbox" },
      ];
    }

    return [
      {
        id: "tenantId",
        label: "Tenant",
        kind: "select",
        options: [
          emptyOption("Select Tenant"),
          ...tenants
            .filter((tenant) => tenant.isActive)
            .map((tenant) =>
              option(
                tenant.tenantId,
                `${tenant.companyName} (${tenant.tenantId})`,
              ),
            ),
        ],
      },
      {
        id: "plantId",
        label: "Plant",
        kind: "select",
        options: [
          emptyOption("Select Plant"),
          ...topology.plants
            .filter(
              (plant) =>
                plant.isActive && (!tenantId || plant.tenantId === tenantId),
            )
            .map((plant) =>
              option(plant.plantId, `${plant.plantName} (${plant.plantId})`),
            ),
        ],
      },
      { id: "productCode", label: "Product Code" },
      { id: "productName", label: "Product Name" },
      { id: "isActive", label: "Active", kind: "checkbox" },
    ];
  }, [
    areaId,
    criticalParameters,
    equipments,
    plantId,
    section,
    tenantId,
    tenants,
    topology.areas,
    topology.plants,
    topology.rooms,
    values.parameterType,
  ]);

  const change = (field: string, value: string | boolean) => {
    setValues((current) => {
      const next = { ...current, [field]: value };
      if (section === "equipments" && typeof value === "string") {
        if (field === "tenantId") {
          next.plantId = "";
          next.areaId = "";
          next.roomId = "";
        }
        if (field === "plantId") {
          next.areaId = "";
          next.roomId = "";
        }
        if (field === "areaId") {
          next.roomId = "";
        }
      }
      if (section === "product-master" && field === "tenantId") {
        next.plantId = "";
      }
      if (section === "critical-parameters" && field === "equipmentId") {
        const equipment = equipments.find(
          (item) => item.equipmentId === String(value),
        );
        if (equipment) {
          next.tenantId = equipment.tenantId;
          next.plantId = equipment.plantId;
        }
      }
      if (section === "critical-parameter-limits" && field === "parameterId") {
        const parameter = criticalParameters.find(
          (item) => item.parameterId === String(value),
        );
        const equipment = equipments.find(
          (item) => item.equipmentId === parameter?.equipmentId,
        );
        next.parameterType = String(parameter?.parameterType ?? "");
        next.equipmentId = String(
          parameter?.equipmentId ?? equipment?.equipmentId ?? next.equipmentId ?? "",
        );
        next.tenantId = String(
          parameter?.tenantId ?? equipment?.tenantId ?? next.tenantId ?? "",
        );
        next.plantId = String(
          parameter?.plantId ?? equipment?.plantId ?? next.plantId ?? "",
        );
        next.lowCriticalValue = "";
        next.highCriticalValue = "";
        next.booleanValue = "";
        next.enumValue = "";
        next.stringValue = "";
      }
      return next;
    });
    setErrors((current) => ({ ...current, [field]: "" }));
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const payload = Object.fromEntries(
      Object.entries(values).map(([key, value]) => [
        key,
        typeof value === "string" ? value.trim() : value,
      ]),
    );
    const selectedEquipment =
      section === "critical-parameters"
        ? equipments.find(
            (item) => item.equipmentId === String(payload.equipmentId ?? ""),
          )
        : undefined;
    const selectedParameter =
      section === "critical-parameter-limits"
        ? criticalParameters.find(
            (item) => item.parameterId === String(payload.parameterId ?? ""),
          )
        : undefined;
    const selectedLimitEquipment =
      section === "critical-parameter-limits"
        ? equipments.find(
            (item) =>
              item.equipmentId ===
              String(selectedParameter?.equipmentId ?? payload.equipmentId ?? ""),
          )
        : undefined;
    const normalizedPayload =
      section === "critical-parameters"
        ? {
            ...payload,
            tenantId: String(payload.tenantId ?? selectedEquipment?.tenantId ?? ""),
            plantId: String(payload.plantId ?? selectedEquipment?.plantId ?? ""),
          }
        : section === "critical-parameter-limits"
          ? (() => {
              const limitType = String(
                payload.parameterType ?? selectedParameter?.parameterType ?? "",
              ).toUpperCase();
              const isBooleanTrue =
                String(payload.booleanValue ?? "").toLowerCase() === "true";

              return {
                ...payload,
                parameterLimitId: String(payload.parameterLimitCode ?? ""),
                parameterType: limitType,
                equipmentId: String(
                  payload.equipmentId ??
                    selectedParameter?.equipmentId ??
                    selectedLimitEquipment?.equipmentId ??
                    "",
                ),
                tenantId: String(
                  payload.tenantId ??
                    selectedParameter?.tenantId ??
                    selectedLimitEquipment?.tenantId ??
                    "",
                ),
                plantId: String(
                  payload.plantId ??
                    selectedParameter?.plantId ??
                    selectedLimitEquipment?.plantId ??
                    "",
                ),
                lowCriticalValue:
                  limitType === "INT" ||
                  limitType === "FLOAT" ||
                  limitType === "RANGE"
                    ? payload.lowCriticalValue
                    : limitType === "BOOLEAN"
                      ? (isBooleanTrue ? 1 : 0)
                      : undefined,
                highCriticalValue:
                  limitType === "INT" ||
                  limitType === "FLOAT" ||
                  limitType === "RANGE"
                    ? payload.highCriticalValue
                    : limitType === "BOOLEAN"
                      ? (isBooleanTrue ? 1 : 0)
                      : undefined,
                booleanValue:
                  limitType === "BOOLEAN" ? isBooleanTrue : undefined,
                enumValue:
                  limitType === "ENUM"
                    ? String(payload.enumValue ?? "")
                    : undefined,
                stringValue:
                  limitType === "STRING"
                    ? String(payload.stringValue ?? "")
                    : undefined,
              };
            })()
        : payload;
    const schema =
      section === "equipments"
        ? updateIiotAssetSchema
        : section === "critical-parameters"
          ? updateCriticalParameterSchema
          : section === "critical-parameter-limits"
            ? updateCriticalParameterLimitSchema
            : updateProductMasterSchema;

    const parsed = schema.safeParse(normalizedPayload);
    if (!parsed.success) {
      setErrors(
        Object.fromEntries(
          parsed.error.issues.map((issue) => [
            String(issue.path[0]),
            issue.message,
          ]),
        ),
      );
      return;
    }

    await onSave(
      parsed.data as
        | UpdateIiotAssetValues
        | UpdateCriticalParameterValues
        | UpdateCriticalParameterLimitValues
        | UpdateProductMasterValues,
    );
  };

  const renderField = (field: EditField) => {
    if (field.kind === "checkbox") {
      return (
        <label key={field.id} className="flex h-9 items-center gap-2 self-end">
          <input
            type="checkbox"
            checked={Boolean(values[field.id])}
            onChange={(event) => change(field.id, event.target.checked)}
            className="h-4 w-4 rounded border-[#BFD0E4] text-primary"
          />
          <span className="type-filter-label text-text-heading">
            {field.label}
          </span>
        </label>
      );
    }

    return (
      <label key={field.id} htmlFor={field.id} className="grid gap-2">
        <span className="type-filter-label text-text-heading">{field.label}</span>
        {field.kind === "select" ? (
          <>
            <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
              <select
                id={field.id}
                value={String(values[field.id] ?? "")}
                onChange={(event) => change(field.id, event.target.value)}
                disabled={field.disabled}
                className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none disabled:cursor-not-allowed"
              >
                {field.options?.map((item) => (
                  <option key={`${field.id}-${item.value}-${item.label}`} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
              <CaretDown size={11} weight="bold" className="pointer-events-none absolute right-3 text-text-secondary" />
            </span>
            {errors[field.id] ? <span className="text-[9px] text-danger">{errors[field.id]}</span> : null}
          </>
        ) : (
          <TextField
            id={field.id}
            type={field.kind === "number" ? "number" : "text"}
            placeholder={field.placeholder}
            value={String(values[field.id] ?? "")}
            readOnly={field.disabled}
            onChange={(event) => change(field.id, event.target.value)}
            error={errors[field.id]}
            containerClassName="module-glass-control !rounded-[4px] !px-3 !py-2"
            labelClassName="type-filter-label text-text-heading"
            inputClassName="type-filter-value placeholder:text-text-secondary read-only:cursor-not-allowed"
            hintClassName="text-[9px]"
          />
        )}
      </label>
    );
  };

  return (
    <Dialog
      isOpen={isOpen}
      title={sectionTitles[section]}
      onClose={onClose}
      widthClassName="max-w-[760px]"
      contentClassName="bg-white"
    >
      <form onSubmit={submit}>
        <div className="grid gap-x-10 gap-y-6 px-6 py-6 md:grid-cols-2">
          {fields.map(renderField)}
        </div>
        <div className="flex justify-end gap-3 border-t border-[#E6E6E6] px-6 py-5">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            rounded="rounded-[4px]"
            className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65"
            onClick={onClose}
            disabled={isSaving}
          >
            Discard Changes
          </Button>
          <Button
            type="submit"
            size="sm"
            rounded="rounded-[4px]"
            className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
            isLoading={isSaving}
          >
            Save Changes
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
